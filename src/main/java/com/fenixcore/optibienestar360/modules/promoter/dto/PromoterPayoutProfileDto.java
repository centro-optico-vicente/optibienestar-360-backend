package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/promoters/{promoterUuid}/payout-profile}
 * and {@code GET /v1/promoter/me/payout-profile} (V182, dossier section V).
 *
 * <p><b>Empty-state contract</b> (1:1 sub-resource, hub ADR 0012): always
 * 200 when the promoter exists. With no row (or a soft-deleted one) the
 * service returns an empty instance — {@code uuid=null}, every payout field
 * {@code null}, {@code exists=false}. 404 is reserved for an unknown
 * promoter. {@code bank}/{@code mobilePaymentBank} serialize as
 * {@code <rel>_Uuid} + {@code <rel>_Display} (ADR 0014).</p>
 */
public record PromoterPayoutProfileDto(
        UUID uuid,
        UUID promoterUuid,

        @Display DisplayRef bank,
        String accountNumber,
        String accountHolderDocumentType,
        String accountHolderDocumentNumber,

        String mobilePaymentPhone,
        @Display DisplayRef mobilePaymentBank,

        String ubiiAccount,
        String ubiiHolderName,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "promoter_payout_profile.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt,

        boolean exists
) {}
