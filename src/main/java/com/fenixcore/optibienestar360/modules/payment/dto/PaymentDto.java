package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

        // Subject (flat)
        UUID membershipUuid,
        UUID memberUuid,
        @Display DisplayRef plan,

        // Payer (flat — null when cash-at-counter)
        UUID payerUserUuid,

        // Money
        @Display(Display.Kind.MONEY) BigDecimal amount,
        String currency,

        // Method
        @Display(Display.Kind.ENUM) PaymentMethod paymentMethod,
        String referenceNumber,

        // Dates
        @Display(Display.Kind.DATE) LocalDate paymentDate,
        @Display(Display.Kind.DATETIME) Instant receivedAt,

        // Allocation
        @Display(Display.Kind.BOOLEAN) boolean inscription,
        @Display(Display.Kind.DATE) LocalDate appliedPeriod,

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
        UUID reviewedByUserUuid,
        @Display(Display.Kind.DATETIME) Instant reviewedAt,
        String reviewReason,

        // One-off discount (V41; null when none applied)
        @Display(Display.Kind.MONEY) BigDecimal discountAmount,
        String discountReason,
        UUID discountedByUserUuid,
        @Display(Display.Kind.DATETIME) Instant discountedAt,

        // Audit
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
