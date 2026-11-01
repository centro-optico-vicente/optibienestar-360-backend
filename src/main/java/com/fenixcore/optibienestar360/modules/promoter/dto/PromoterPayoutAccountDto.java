package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.payment.dto.AccountMethodDto;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Output DTO for {@code /v1/admin/advisors/{promoterUuid}/payout-accounts}
 * and {@code GET /v1/me/advisor/payout-accounts} (V184).
 *
 * <p>{@code bank} serializes as {@code <rel>_Uuid} + {@code <rel>_Display}
 * (ADR 0014). An account accepts several methods (V217), so they come as a
 * list plus {@code paymentMethodCodes}, so the payout form can match an
 * account against the selected method.</p>
 */
public record PromoterPayoutAccountDto(
        UUID uuid,
        UUID promoterUuid,

        List<AccountMethodDto> paymentMethods,
        List<String> paymentMethodCodes,
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
