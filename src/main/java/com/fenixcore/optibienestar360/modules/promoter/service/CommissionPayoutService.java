package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutResponse.PromoterPayoutSummary;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp.TopUpStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride.OverrideStatus;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRetroactiveTopUpRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Period-close service for the promoter earnings ledger. Given a date
 * range + payout reference, marks every PENDING row PAID across the three
 * sources a settlement close can touch (hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3, PR4):
 * direct {@link Commission}s, {@link PromoterHierarchyOverride}s, and
 * {@link CommissionRetroactiveTopUp}s — groups by promoter, emits a
 * per-promoter CSV breakdown (one row per line, tagged with its {@code
 * concept}), and emails each promoter the summary + CSV.
 *
 * <p>Distinct from {@link CommissionService} (singular, calc engine wired
 * into {@code PaymentsService.approve}) and {@link CommissionsService}
 * (plural, admin read queue). This service owns ONE operation: close a
 * period.</p>
 *
 * <p><b>Currency handling</b>: the engine groups + sums per promoter
 * ASSUMING a single currency per promoter's batch. Mixed-currency
 * batches (some commissions in USD, others in VES on the same promoter)
 * are summed under whichever currency appears first; this is a
 * known-and-acceptable v1 trade-off since v1 only ever produces USD
 * commissions per V26 default. If multi-currency lands in v2, the
 * service will need to split per (promoter, currency) — captured in
 * vertical-8 § Pendientes de análisis when that case appears.</p>
 *
 * <p><b>Email failures don't roll back the payout</b>: the DB writes
 * commit before the emails fire. A promoter's email failure is logged
 * and surfaced in the response (the rows stay PAID; admin can re-send
 * manually). Same best-effort policy as {@code PaymentsService} email
 * dispatch.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionPayoutService {

    private static final String TEMPLATE = "commission-payout";
    private static final String SUBJECT_KEY = "email.commission.payout.subject";

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CommissionRepository commissionRepository;
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final CommissionRetroactiveTopUpRepository topUpRepository;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final CommissionAuditRecorder auditRecorder;

    @Transactional
    public CommissionPayoutResponse execute(CommissionPayoutRequest request) {
        validatePeriod(request);

        List<Commission> pendingCommissions = commissionRepository.findPendingForPeriod(
                request.periodStart(), request.periodEnd());
        List<PromoterHierarchyOverride> pendingOverrides = overrideRepository.findPendingForPeriod(
                request.periodStart(), request.periodEnd());
        List<CommissionRetroactiveTopUp> pendingTopUps = topUpRepository.findPendingForPeriod(
                request.periodStart(), request.periodEnd());

        boolean isDryRun = Boolean.TRUE.equals(request.dryRun());
        Instant executedAt = Instant.now();

        Map<Promoter, PromoterBatch> byPromoter = groupByPromoter(pendingCommissions, pendingOverrides, pendingTopUps);

        List<PromoterPayoutSummary> summaries = new ArrayList<>(byPromoter.size());
        BigDecimal grandTotal = BigDecimal.ZERO;
        String firstCurrency = null;
        int totalLines = 0;

        for (Map.Entry<Promoter, PromoterBatch> entry : byPromoter.entrySet()) {
            Promoter promoter = entry.getKey();
            PromoterBatch batch = entry.getValue();
            BigDecimal promoterTotal = batch.total();
            String currency = batch.currency().getCode();
            String csv = buildCsv(batch);
            int lineCount = batch.lineCount();

            boolean emailDispatched = false;
            String emailFailure = null;

            if (!isDryRun) {
                markPaid(batch, request.payoutReference(), executedAt);
                EmailDispatchResult mail = sendPromoterEmail(promoter, request, promoterTotal,
                        currency, lineCount, csv);
                emailDispatched = mail.dispatched;
                emailFailure = mail.failureReason;
            }

            summaries.add(new PromoterPayoutSummary(
                    promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName(),
                    lineCount, promoterTotal, currency, csv, emailDispatched, emailFailure));

            grandTotal = grandTotal.add(promoterTotal);
            totalLines += lineCount;
            if (firstCurrency == null) firstCurrency = currency;
        }

        return new CommissionPayoutResponse(
                request.periodStart(), request.periodEnd(),
                request.payoutReference(),
                isDryRun,
                byPromoter.size(),
                totalLines,
                grandTotal,
                firstCurrency,
                executedAt,
                summaries);
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    /** Per-promoter batch of everything a period close can pay — the three PENDING sources §3 consolidates. */
    private record PromoterBatch(
            List<Commission> commissions,
            List<PromoterHierarchyOverride> overrides,
            List<CommissionRetroactiveTopUp> topUps
    ) {
        static PromoterBatch empty() {
            return new PromoterBatch(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        int lineCount() {
            return commissions.size() + overrides.size() + topUps.size();
        }

        BigDecimal total() {
            BigDecimal total = BigDecimal.ZERO;
            for (Commission c : commissions) total = total.add(c.getAmount());
            for (PromoterHierarchyOverride o : overrides) total = total.add(o.getAmount());
            for (CommissionRetroactiveTopUp t : topUps) total = total.add(t.getRetroAmount());
            return total;
        }

        /** First non-empty source's currency — same "assume uniform per batch" trade-off as the class-level Javadoc. */
        Currency currency() {
            if (!commissions.isEmpty()) return commissions.get(0).getCurrency();
            if (!overrides.isEmpty()) return overrides.get(0).getCurrency();
            return topUps.get(0).getCurrency();
        }
    }

    private static void validatePeriod(CommissionPayoutRequest req) {
        if (req.periodStart().isAfter(req.periodEnd())) {
            throw new IllegalArgumentException("commission.payout.period.invalid");
        }
    }

    /** Preserves promoter ordering (each repo returns ordered by promoter_id). */
    private static Map<Promoter, PromoterBatch> groupByPromoter(
            List<Commission> commissions, List<PromoterHierarchyOverride> overrides, List<CommissionRetroactiveTopUp> topUps) {
        Map<Promoter, PromoterBatch> map = new LinkedHashMap<>();
        for (Commission c : commissions) {
            map.computeIfAbsent(c.getPromoter(), k -> PromoterBatch.empty()).commissions().add(c);
        }
        for (PromoterHierarchyOverride o : overrides) {
            map.computeIfAbsent(o.getPromoter(), k -> PromoterBatch.empty()).overrides().add(o);
        }
        for (CommissionRetroactiveTopUp t : topUps) {
            map.computeIfAbsent(t.getPromoter(), k -> PromoterBatch.empty()).topUps().add(t);
        }
        return map;
    }

    /**
     * Audits each commission row individually (spec 16-audit.md) — a period
     * close touches every promoter's rows in one call, but the audit trail
     * still reads per-commission, same as any other update. Overrides/
     * top-ups don't have a dedicated audit recorder yet — same documented
     * gap {@code HierarchyOverrideReRatingService} already carries.
     */
    private void markPaid(PromoterBatch batch, String payoutReference, Instant at) {
        for (Commission c : batch.commissions()) {
            Map<String, Object> before = auditRecorder.snapshot(c);
            c.setStatus(CommissionStatus.PAID.name());
            c.setPaidAt(at);
            c.setPayoutReference(payoutReference);
            auditRecorder.recordUpdate(c.getUuid(), before, auditRecorder.snapshot(c));
        }
        for (PromoterHierarchyOverride o : batch.overrides()) {
            o.setStatus(OverrideStatus.PAID.name());
            o.setPaidAt(at);
            o.setPayoutReference(payoutReference);
        }
        for (CommissionRetroactiveTopUp t : batch.topUps()) {
            t.setStatus(TopUpStatus.PAID.name());
            t.setPaidAt(at);
            t.setPayoutReference(payoutReference);
        }
    }

    /**
     * CSV columns: concept, reference, basis, amount, currency. {@code
     * concept} distinguishes a plain direct commission from a hierarchy
     * override or a retroactive top-up; {@code reference} is the member
     * uuid for a commission, empty for an override (no single member to
     * point at), or the settlement period for a top-up. Header row included
     * so promoters can open the file in Excel without manual setup.
     */
    private static String buildCsv(PromoterBatch batch) {
        StringBuilder sb = new StringBuilder(128 + batch.lineCount() * 64);
        sb.append("concept,reference,basis,amount,currency\n");
        for (Commission c : batch.commissions()) {
            appendCsvRow(sb,
                    c.getAppliesTo() != null ? c.getAppliesTo().name() : "COMMISSION",
                    c.getMember() != null ? String.valueOf(c.getMember().getUuid()) : "",
                    c.getCalculationBasis(), c.getAmount(),
                    c.getCurrency() != null ? c.getCurrency().getCode() : "");
        }
        for (PromoterHierarchyOverride o : batch.overrides()) {
            appendCsvRow(sb,
                    "HIERARCHY_OVERRIDE_" + (o.getCategory() != null ? o.getCategory().name() : ""),
                    "",
                    o.getBasisAmount(), o.getAmount(),
                    o.getCurrency() != null ? o.getCurrency().getCode() : "");
        }
        for (CommissionRetroactiveTopUp t : batch.topUps()) {
            appendCsvRow(sb,
                    "RETROACTIVE_TOPUP_" + (t.getLedgerType() != null ? t.getLedgerType().name() : ""),
                    CSV_DATE.format(t.getPeriodStart()) + ".." + CSV_DATE.format(t.getPeriodEnd()),
                    t.getBasisAmount(), t.getRetroAmount(),
                    t.getCurrency() != null ? t.getCurrency().getCode() : "");
        }
        return sb.toString();
    }

    private static void appendCsvRow(StringBuilder sb, String concept, String reference,
                                     BigDecimal basis, BigDecimal amount, String currency) {
        sb.append(concept).append(',')
          .append(reference).append(',')
          .append(basis != null ? basis.toPlainString() : "").append(',')
          .append(amount.toPlainString()).append(',')
          .append(currency).append('\n');
    }

    private EmailDispatchResult sendPromoterEmail(Promoter promoter,
                                                  CommissionPayoutRequest req,
                                                  BigDecimal totalAmount,
                                                  String currency,
                                                  int lineCount,
                                                  String csv) {
        String to = promoter.getEmail();
        if (to == null || to.isBlank()) {
            log.info("Promoter {} has no email — payout marked PAID but no notification sent",
                    promoter.getReferralCode());
            return EmailDispatchResult.skipped("no_email_on_promoter");
        }

        Locale locale = Locale.forLanguageTag("es");  // promoters default to es-VE
        String subject = messageSource.getMessage(SUBJECT_KEY, null, locale);

        Map<String, Object> vars = new HashMap<>();
        vars.put("promoterName", Optional.ofNullable(promoter.getDisplayName()).orElse(""));
        vars.put("periodStart", req.periodStart());
        vars.put("periodEnd", req.periodEnd());
        vars.put("payoutReference", req.payoutReference());
        vars.put("commissionCount", lineCount);
        vars.put("totalAmount", totalAmount);
        vars.put("currency", currency);
        vars.put("csv", csv);

        try {
            emailService.sendTemplated(to, subject, TEMPLATE, locale, vars);
            return EmailDispatchResult.ok();
        } catch (RuntimeException ex) {
            log.error("Failed to dispatch payout email to promoter {}",
                    promoter.getReferralCode(), ex);
            return EmailDispatchResult.failed(ex.getMessage());
        }
    }

    private record EmailDispatchResult(boolean dispatched, String failureReason) {
        static EmailDispatchResult ok() {
            return new EmailDispatchResult(true, null);
        }
        static EmailDispatchResult skipped(String reason) {
            return new EmailDispatchResult(false, reason);
        }
        static EmailDispatchResult failed(String reason) {
            return new EmailDispatchResult(false, reason);
        }
    }

    /** Defaults the period anchor of LocalDate to first-of-month for symmetric reporting. */
    public static LocalDate normalizeMonthStart(LocalDate d) {
        return d.withDayOfMonth(1);
    }
}
