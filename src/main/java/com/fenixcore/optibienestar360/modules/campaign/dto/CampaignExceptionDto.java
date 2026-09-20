package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionException;

import java.time.Instant;
import java.util.UUID;

/**
 * Output row for {@code GET /v1/admin/campaigns/{uuid}/exceptions} — one
 * manual override ({@link CampaignTransactionException}). Exactly one of
 * {@code payment}/{@code membership} is non-null, mirroring the entity's own
 * one-target-required shape.
 */
public record CampaignExceptionDto(
        UUID uuid,
        @Display(Display.Kind.ENUM) CampaignTransactionException.ExceptionAction action,
        String reason,
        UUID createdByUuid,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display DisplayRef payment,
        @Display DisplayRef membership) {
}
