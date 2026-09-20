package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionException;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Exactly one of {@code paymentUuid} / {@code membershipUuid} must be present — validated in the service. */
public record CampaignExceptionRequest(
        UUID paymentUuid,
        UUID membershipUuid,
        @NotNull CampaignTransactionException.ExceptionAction action,
        String reason) {
}
