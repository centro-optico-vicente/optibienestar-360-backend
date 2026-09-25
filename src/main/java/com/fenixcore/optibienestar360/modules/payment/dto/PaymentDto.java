package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/payments/{uuid}} (single + the
 * response of POST). Plan / member identification kept flat so the admin
 * UI can render a payment card without a follow-up GET.
 *
 * <p>Presentational scalars (amounts, method, dates, status) carry a
 * localized {@code _Display} sibling (hub ADR 0014) so the frontend renders
 * them without re-formatting.</p>
 *
 * <p>The {@code support_file_*} columns expose the file metadata (name,
 * size, content type) but never the raw URL — the presigned URL endpoint
 * (separate bullet) is what the frontend uses to actually download the
 * file. {@code supportFileAvailable} signals whether a proof was attached
 * regardless of whether R2 wiring is active in this environment.</p>
 */
public record PaymentDto(
        UUID uuid,

        // Header (V117, hub plan payments-unification) — `direction` distinguishes
        // a collection (IN, this record's original shape) from a commission payout
        // (OUT, written by CommissionPayoutService, never through this DTO's own
        // create endpoint). `paymentType` is the REASON (payment_categories) — not
        // to be confused with `paymentMethod` below (the line-level HOW).
        String direction,
        @Display DisplayRef paymentType,
        @Display DisplayRef person,
        @Display DisplayRef promoter,

        // Subject (flat refs — member/membership resolve to a readable
        // _Display via DisplayRefs, never a bare UUID; see ADR 0014). Null for
        // `direction=OUT` — a commission payout has no membership (V120).
        @Display DisplayRef membership,
        @Display DisplayRef member,
        @Display DisplayRef plan,

        /** Simple-reporting mirror of the campaign this payment counted towards (V124) — read-only, see {@code Payment#campaign}. */
        @Display DisplayRef campaign,

        // Payer (flat — null when cash-at-counter)
        UUID payerUserUuid,

        // Money (ADR 0015 §5/§6/§7 — currency_Code is the real denomination;
        // amountConverted/convertedCurrency_Code/exchangeRateUsed/exchangeRateDate
        // are the persisted payout snapshot for an approved payment settling in a
        // different currency than it was registered in — never recomputed)
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount,
        String currency_Code,
        @Display DisplayRef currency,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "convertedCurrency_Code") BigDecimal amountConverted,
        String convertedCurrency_Code,
        @Display DisplayRef convertedCurrency,
        BigDecimal exchangeRateUsed,
        @Display(Display.Kind.DATE) LocalDate exchangeRateDate,

        // Method — sourced from the (today, single) payment_lines row; V115 catalog code
        @Display(Display.Kind.ENUM) String paymentMethod,
	String paymentMethodDescription,
	boolean paymentMethodMandatoryIdentification,
	boolean paymentMethodMandatoryBank,
	boolean paymentMethodMandatoryBankAccount,
	boolean paymentMethodMandatoryAccountType,
	boolean paymentMethodMandatoryAccountCode,
	boolean paymentMethodMandatoryPhone,
	boolean paymentMethodMandatoryEmail,
	boolean paymentMethodMandatoryReferenceNumber,
        String referenceNumber,
	// Line — bank (V117 FK, ADR 0014 _Display pair) only populated when
	// paymentMethodMandatoryBank is true (PAGO_MOVIL, CHECK, BANK_TRANSFER, BANK_DEPOSIT)
	@Display DisplayRef bank,
	String identification,
	String bankAccountType,
	String bankAccountCode,
	String bankAccountIdentifier,
	String phone,
	String email,

        /**
         * Full lines collection (V117 lines feature) — every {@code
         * payment_lines} row, in addition to the flattened first-line fields
         * above (kept as-is for backward compatibility). A payment with a
         * single line still populates both; a genuinely split payment only
         * shows its first line in the flat fields, the full split here.
         */
        List<PaymentLineDto> lines,

        // Dates
	@Display(Display.Kind.DATETIME) Instant paymentDate,
        @Display(Display.Kind.DATETIME) Instant receivedAt,

        // Allocation
        @Display(Display.Kind.BOOLEAN) boolean inscription,
        @Display(Display.Kind.DATE) LocalDate appliedPeriod,

        /**
         * First day of the LAST month covered by a multi-month advance
         * payment (V154) — {@code null} means single-month, same as
         * {@link #appliedPeriod}'s historical meaning.
         */
        @Display(Display.Kind.DATE) LocalDate coverageThroughPeriod,

        // Proof of payment (metadata only; raw URL is fetched via
        // /support presigned endpoint when wired up)
        @Display(Display.Kind.BOOLEAN) boolean supportFileAvailable,
        String supportFileName,
        String supportFileContentType,
        Long supportFileSizeBytes,

        // Admin notes (visible only to admin per controller-level perm)
        String adminNotes,

        // Review state (status is the BaseEntity column)
        @Display(value = Display.Kind.ENUM, enumScope = "payment.status") String status,
        @Display DisplayRef reviewedBy,
        @Display(Display.Kind.DATETIME) Instant reviewedAt,
        String reviewReason,

        // One-off discount (V41; null when none applied)
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal discountAmount,
        String discountReason,
        UUID discountedByUserUuid,
        @Display(Display.Kind.DATETIME) Instant discountedAt,

        // Audit
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
