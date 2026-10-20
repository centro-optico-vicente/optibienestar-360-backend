package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code /v1/admin/advisors/{promoterUuid}/payout-accounts}
 * and {@code GET /v1/advisors/me/payout-accounts} (V184).
 *
 * <p>{@code paymentMethod}/{@code bank} serialize as {@code <rel>_Uuid} +
 * {@code <rel>_Display} (ADR 0014); {@code paymentMethodCode} is exposed too
 * so the payout form can match an account against the selected method.</p>
 */
public record PromoterPayoutAccountDto(
        UUID uuid,
        UUID promoterUuid,

        @Display DisplayRef paymentMethod,
        String paymentMethodCode,
        String label,

        @Display DisplayRef bank,
        String accountType,
        String accountNumber,
        String accountIdentifier,

        String holderName,
        String holderDocumentType,
        String holderDocumentNumber,

        String phone,
        String email,

        @Display(Display.Kind.BOOLEAN) boolean isDefault,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "promoter_payout_account.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
