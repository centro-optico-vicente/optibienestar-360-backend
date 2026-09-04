package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutResponse.PromoterPayoutSummary;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
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
 * Period-close service for the commissions ledger. Given a date range +
 * payout reference, marks every PENDING commission inside the range as
 * PAID, groups by promoter, emits a per-promoter CSV breakdown, and
 * emails each promoter the summary + CSV.
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
 * and surfaced in the response (the row stays PAID; admin can re-send
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
    private final EmailService emailService;
    private final MessageSource messageSource;

    @Transactional
    public CommissionPayoutResponse execute(CommissionPayoutRequest request) {
        validatePeriod(request);

        List<Commission> pending = commissionRepository.findPendingForPeriod(
                request.periodStart(), request.periodEnd());

        boolean isDryRun = Boolean.TRUE.equals(request.dryRun());
        Instant executedAt = Instant.now();

        Map<Promoter, List<Commission>> byPromoter = groupByPromoter(pending);

        List<PromoterPayoutSummary> summaries = new ArrayList<>(byPromoter.size());
        BigDecimal grandTotal = BigDecimal.ZERO;
        String firstCurrency = null;

        for (Map.Entry<Promoter, List<Commission>> entry : byPromoter.entrySet()) {
            Promoter promoter = entry.getKey();
            List<Commission> rows = entry.getValue();
            BigDecimal promoterTotal = sumAmount(rows);
            String currency = rows.get(0).getCurrency().getCode();
            String csv = buildCsv(rows);

            boolean emailDispatched = false;
            String emailFailure = null;

            if (!isDryRun) {
                markPaid(rows, request.payoutReference(), executedAt);
                EmailDispatchResult mail = sendPromoterEmail(promoter, request, promoterTotal,
                        currency, rows.size(), csv);
                emailDispatched = mail.dispatched;
                emailFailure = mail.failureReason;
            }

            summaries.add(new PromoterPayoutSummary(
                    promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName(),
                    rows.size(), promoterTotal, currency, csv, emailDispatched, emailFailure));

            grandTotal = grandTotal.add(promoterTotal);
            if (firstCurrency == null) firstCurrency = currency;
        }

        return new CommissionPayoutResponse(
                request.periodStart(), request.periodEnd(),
                request.payoutReference(),
                isDryRun,
                byPromoter.size(),
                pending.size(),
                grandTotal,
                firstCurrency,
                executedAt,
                summaries);
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    private static void validatePeriod(CommissionPayoutRequest req) {
        if (req.periodStart().isAfter(req.periodEnd())) {
            throw new IllegalArgumentException("commission.payout.period.invalid");
        }
    }

    /** Preserves promoter ordering (the repo returns ordered by promoter_id). */
    private static Map<Promoter, List<Commission>> groupByPromoter(List<Commission> rows) {
        Map<Promoter, List<Commission>> map = new LinkedHashMap<>();
        for (Commission c : rows) {
            map.computeIfAbsent(c.getPromoter(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    private static BigDecimal sumAmount(List<Commission> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (Commission c : rows) {
            total = total.add(c.getAmount());
        }
        return total;
    }

    private static void markPaid(List<Commission> rows, String payoutReference, Instant at) {
        for (Commission c : rows) {
            c.setStatus(CommissionStatus.PAID.name());
            c.setPaidAt(at);
            c.setPayoutReference(payoutReference);
        }
    }

    /**
     * CSV columns: payment_date, member_uuid, applies_to, basis, amount, currency.
     * Header row included so promoters can open the file in Excel without
     * manual setup. Quoted only when the field contains a comma or a quote.
     */
    private static String buildCsv(List<Commission> rows) {
        StringBuilder sb = new StringBuilder(128 + rows.size() * 64);
        sb.append("payment_date,member_uuid,applies_to,basis,amount,currency\n");
        for (Commission c : rows) {
            sb.append(c.getPayment() != null && c.getPayment().getPaymentDate() != null
                    ? CSV_DATE.format(c.getPayment().getPaymentDate()) : "")
              .append(',')
              .append(c.getMember() != null ? c.getMember().getUuid() : "")
              .append(',')
              .append(c.getAppliesTo() != null ? c.getAppliesTo().name() : "")
              .append(',')
              .append(c.getCalculationBasis() != null ? c.getCalculationBasis().toPlainString() : "")
              .append(',')
              .append(c.getAmount().toPlainString())
              .append(',')
              .append(c.getCurrency() != null ? c.getCurrency().getCode() : "")
              .append('\n');
        }
        return sb.toString();
    }

    private EmailDispatchResult sendPromoterEmail(Promoter promoter,
                                                  CommissionPayoutRequest req,
                                                  BigDecimal totalAmount,
                                                  String currency,
                                                  int commissionCount,
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
        vars.put("commissionCount", commissionCount);
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
