package com.fenixcore.optibienestar360.modules.campaign.dto;

import org.springframework.data.domain.Page;

/**
 * The campaign "Pagos" section ({@code GET /v1/admin/campaigns/{uuid}/payments},
 * V260, hub ADR 0038 §9): totals over every slot of the campaign (not the
 * filtered page) and the filtered page of rows.
 */
public record CampaignPaymentsDto(Totals totals, Page<CampaignPaymentRowDto> page) {

    /**
     * {@code pendingInReview} = PENDING slots with a qualifying payment in
     * review; {@code pendingWithoutPayment} = PENDING slots without one.
     */
    public record Totals(int confirmed, int pendingInReview, int pendingWithoutPayment, int expired) {}
}
