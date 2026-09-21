package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCategory;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCategoryRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Period-close service for the promoter earnings ledger. Given a date
 * range + payout reference, marks every payable row PAID across the three
 * sources a settlement close can touch (hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3-4, PR4/PR6):
 * direct {@link Commission}s, {@link PromoterHierarchyOverride}s, and
 * {@link CommissionRetroactiveTopUp}s — groups by promoter, emits a
 * per-promoter CSV breakdown (one row per line, tagged with its {@code
 * concept}), and emails each promoter the summary + CSV.
 *
 * <p><b>Commercial approval gate (V107, PR6)</b>: administración can only
 * ever disburse what gerencia comercial already approved. Concretely:</p>
 * <ul>
 *   <li>{@link Commission}s must be {@code APPROVED} — a still-{@code
 *       PENDING} (unreviewed) or {@code REJECTED} row is never picked up,
 *       via {@link CommissionRepository#findApprovedForPeriod}.</li>
 *   <li>{@link PromoterHierarchyOverride}s have no approval state of their
 *       own (only the direct commission does — the rest inherit); a
 *       PENDING override is only payable once the {@link Commission} that
 *       ultimately funds it (walking {@code source_override_id} up to the
 *       root {@code source_commission_id}, since a level-3+ override is
 *       funded by the override below it, never directly by a commission —
 *       see {@link #isRootCommissionApproved}) is itself {@code APPROVED}.
 *       A rejected root already had its whole dependent chain {@code
 *       VOIDED} by {@code CommissionApprovalService}, so this filter is
 *       really "still-{@code PENDING}-at-the-root" exclusion in practice.</li>
 *   <li>{@link CommissionRetroactiveTopUp}s need no separate gate: {@code
 *       CommissionRetroactiveTopUpService} only ever computes them from
 *       already-{@code PAID} commissions/overrides — and, by this very
 *       gate, nothing can reach {@code PAID} without having been {@code
 *       APPROVED} first. The approval requirement is satisfied
 *       transitively, by construction.</li>
 * </ul>
 *
 * <p>Re-rating ({@code CommissionReRatingService}/{@code
 * HierarchyOverrideReRatingService}, PR3) deliberately still targets {@code
 * PENDING} rows, not {@code APPROVED} ones — it must run <i>before</i> the
 * commercial review, so what comercial approves is already the period's
 * correct final amount. Re-rating an already-approved row would silently
 * change a number someone signed off on; that would need an explicit
 * re-approval flow, out of scope here.</p>
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
 *
 * <p><b>Real payout {@link Payment} rows (V117-V120, hub plan
 * payments-unification)</b>: each promoter's batch is split by concept —
 * {@code COMMISSION_REGULAR} (direct commissions), {@code
 * HIERARCHY_OVERRIDE}, {@code RETROACTIVE_TOPUP} — because {@code
 * payments.payment_type_id} is a single {@link PaymentCategory} per header,
 * and a batch can legitimately mix concepts for the same promoter. Up to 3
 * {@code direction=OUT} {@link Payment} headers are created per promoter per
 * run, each with one {@link PaymentLine} using {@link
 * CommissionPayoutRequest#paymentMethod()} (defaults to {@code OTHER} when
 * omitted — same placeholder role the pre-V117 free-text {@code
 * payoutReference} played; the admin can edit the line's method/reference
 * later like any other payment). The rows themselves link back via the new
 * {@code payoutPayment} FK (V118); {@code payoutReference} (free text) is
 * kept unchanged alongside it — still the human-readable batch id, now
 * redundant with (not replaced by) the real FK.</p>
 *
 * <p><b>The {@code INSTITUCION} system promoter has no {@link
 * Promoter#getPerson()}</b> ({@code promoters.person_id} nullable, V25) —
 * {@code payments.person_id} is {@code NOT NULL}, so no payout {@link
 * Payment} can be created for it. Its rows still get marked {@code PAID}
 * via {@code payoutReference} exactly as before; {@code payoutPayment} stays
 * {@code null} for them. This is the one case where the real-FK linkage is
 * structurally impossible, not just unimplemented.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionPayoutService {

    private static final String TEMPLATE = "commission-payout";
    private static final String SUBJECT_KEY = "email.commission.payout.subject";

    /** No method is chosen at payout time today — see class Javadoc. */
    private static final String DEFAULT_PAYOUT_METHOD_CODE = "OTHER";

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CommissionRepository commissionRepository;
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final CommissionRetroactiveTopUpRepository topUpRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCategoryRepository paymentCategoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final CommissionAuditRecorder auditRecorder;
    private final ConversionEnricher conversionEnricher;

    @Transactional
    public CommissionPayoutResponse execute(CommissionPayoutRequest request, UUID actorUserUuid) {
        validatePeriod(request);

        List<Commission> approvedCommissions = commissionRepository.findApprovedForPeriod(
                request.periodStart(), request.periodEnd());
        List<PromoterHierarchyOverride> payableOverrides = overrideRepository.findPendingForPeriod(
                request.periodStart(), request.periodEnd()).stream()
                .filter(this::isRootCommissionApproved)
                .toList();
        List<CommissionRetroactiveTopUp> pendingTopUps = topUpRepository.findPendingForPeriod(
                request.periodStart(), request.periodEnd());

        boolean isDryRun = Boolean.TRUE.equals(request.dryRun());
        Instant executedAt = Instant.now();
        // Only needed to stamp the payout Payment headers' reviewedBy — a dry
        // run creates no rows, so resolving it there would be pure waste.
        User actor = isDryRun ? null : userRepository.findByUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        Map<Promoter, PromoterBatch> byPromoter = groupByPromoter(approvedCommissions, payableOverrides, pendingTopUps);

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
                String methodCode = request.paymentMethod() != null && !request.paymentMethod().isBlank()
                        ? request.paymentMethod() : DEFAULT_PAYOUT_METHOD_CODE;
                markPaid(promoter, batch, request.payoutReference(), executedAt, actor, methodCode);
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

    /**
     * Walks {@code sourceOverride} up to the root {@code sourceCommission}
     * (the cascade never skips a level, so a level-3+ override is always
     * funded by the override immediately below it, never directly by a
     * commission) and returns whether that root is {@code APPROVED}. A
     * level-2 override's own {@code sourceCommission} is already the root —
     * the loop below terminates on the first row that has one.
     */
    private boolean isRootCommissionApproved(PromoterHierarchyOverride override) {
        PromoterHierarchyOverride current = override;
        while (current.getSourceCommission() == null) {
            if (current.getSourceOverride() == null) {
                // Should be unreachable (V102 CHECK enforces XOR) — treat as unpayable rather than throw.
                log.warn("Hierarchy override {} has neither source_commission nor source_override", current.getUuid());
                return false;
            }
            current = current.getSourceOverride();
        }
        return CommissionStatus.APPROVED.name().equals(current.getSourceCommission().getStatus());
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
     *
     * <p>Also creates the real payout {@link Payment} row(s) — one per
     * non-empty concept in this promoter's batch (see class Javadoc) — and
     * links every commission/override/top-up back to it via {@code
     * payoutPayment}. {@code payoutReference} (free text) is still set on
     * every row unconditionally, same as before this refactor.</p>
     */
    private void markPaid(Promoter promoter, PromoterBatch batch, String payoutReference, Instant at, User actor, String methodCode) {
        Payment commissionsPayment = createPayoutPayment(promoter, "COMMISSION_REGULAR",
                batch.commissions(), Commission::getAmount, Commission::getCurrency, payoutReference, at, actor, methodCode);
        Payment overridesPayment = createPayoutPayment(promoter, "HIERARCHY_OVERRIDE",
                batch.overrides(), PromoterHierarchyOverride::getAmount, PromoterHierarchyOverride::getCurrency, payoutReference, at, actor, methodCode);
        Payment topUpsPayment = createPayoutPayment(promoter, "RETROACTIVE_TOPUP",
                batch.topUps(), CommissionRetroactiveTopUp::getRetroAmount, CommissionRetroactiveTopUp::getCurrency, payoutReference, at, actor, methodCode);

        for (Commission c : batch.commissions()) {
            Map<String, Object> before = auditRecorder.snapshot(c);
            c.setStatus(CommissionStatus.PAID.name());
            c.setPaidAt(at);
            c.setPayoutReference(payoutReference);
            c.setPayoutPayment(commissionsPayment);
            ConversionEnricher.RateSnapshot paidRate = conversionEnricher.officialRateAt(c.getCurrency(), at);
            c.setExchangeRateAtPaid(paidRate.rate());
            c.setPaidRateDate(paidRate.date());
            auditRecorder.recordUpdate(c.getUuid(), before, auditRecorder.snapshot(c));
        }
        for (PromoterHierarchyOverride o : batch.overrides()) {
            o.setStatus(OverrideStatus.PAID.name());
            o.setPaidAt(at);
            o.setPayoutReference(payoutReference);
            o.setPayoutPayment(overridesPayment);
        }
        for (CommissionRetroactiveTopUp t : batch.topUps()) {
            t.setStatus(TopUpStatus.PAID.name());
            t.setPaidAt(at);
            t.setPayoutReference(payoutReference);
            t.setPayoutPayment(topUpsPayment);
        }
    }

    /**
     * One {@code direction=OUT} {@link Payment} header (+ its single {@link
     * PaymentLine}) for one (promoter, concept) pair, or {@code null} when
     * {@code items} is empty (nothing to pay for that concept this run) or
     * the promoter has no {@code Person} to bill to (the {@code INSTITUCION}
     * system promoter — see class Javadoc). Status is {@code APPROVED}
     * immediately: the underlying commissions already passed the commercial
     * approval gate (class Javadoc "Commercial approval gate"), so the
     * payout record itself needs no separate PENDING review step.
     */
    private <T> Payment createPayoutPayment(Promoter promoter, String categoryCode, List<T> items,
                                            Function<T, BigDecimal> amountOf,
                                            Function<T, Currency> currencyOf,
                                            String payoutReference, Instant at, User actor, String methodCode) {
        if (items.isEmpty() || promoter.getPerson() == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (T item : items) {
            total = total.add(amountOf.apply(item));
        }
        Currency currency = currencyOf.apply(items.get(0));

        PaymentCategory category = paymentCategoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new NoSuchElementException("payment_category.not_found: " + categoryCode));

        Payment payment = new Payment();
        payment.setDirection("OUT");
        payment.setPaymentType(category);
        payment.setPerson(promoter.getPerson());
        payment.setPromoter(promoter);
        payment.setAmount(total);
        payment.setCurrency(currency);
        payment.setPaymentDate(at);
        payment.setStatus(Payment.PaymentStatus.APPROVED.name());
        payment.setReviewedBy(actor);
        payment.setReviewedAt(at);

        PaymentLine line = new PaymentLine();
        line.setPayment(payment);
        line.setPaymentType(paymentMethodRepository.findByCode(methodCode)
                .orElseThrow(() -> new NoSuchElementException("payment_method.not_found: " + methodCode)));
        line.setAmount(total);
        line.setCurrency(currency);
        line.setReferenceNumber(payoutReference);
        line.setStatus(Payment.PaymentStatus.APPROVED.name());
        line.setReviewedBy(actor);
        line.setReviewedAt(at);
        payment.getLines().add(line);

        return paymentRepository.save(payment);
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
