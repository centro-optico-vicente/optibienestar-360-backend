package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

/**
 * Reconciliation row of {@code GET /v1/admin/campaigns/{uuid}/ally-benefits}
 * (V257, hub ADR 0038 §7): vouchers of one ally-benefit promotion by status.
 * The cost is absorbed by the ally and only recorded here.
 */
public record CampaignAllyBenefitDto(
        @Display DisplayRef ally,
        @Display DisplayRef promotion,
        long granted,
        long available,
        long redeemed,
        long expired,
        long canceled,
        long redeemedUnits
) {}
