package com.fenixcore.optibienestar360.modules.payment.dto;

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
        UUID planUuid,
        String planCode,

        // Payer (flat — null when cash-at-counter)
        UUID payerUserUuid,

        // Money
        BigDecimal amount,
        String currency,

        // Method
        PaymentMethod paymentMethod,
        String referenceNumber,

        // Dates
        LocalDate paymentDate,
        Instant receivedAt,

        // Allocation
        boolean inscription,
        LocalDate appliedPeriod,

        // Proof of payment (metadata only; raw URL is fetched via
        // /support presigned endpoint when wired up)
        boolean supportFileAvailable,
        String supportFileName,
        String supportFileContentType,
        Long supportFileSizeBytes,

        // Admin notes (visible only to admin per controller-level perm)
        String adminNotes,

        // Review state (status is the BaseEntity column)
        String status,
        UUID reviewedByUserUuid,
        Instant reviewedAt,
        String reviewReason,

        // Audit
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
