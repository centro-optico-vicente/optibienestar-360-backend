package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionLink;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Output row for {@code GET /v1/admin/campaigns/{uuid}/transactions} — one
 * resolved {@link CampaignTransactionLink}. {@code amount} is populated only
 * for a payment-backed link (null for a membership/enrollment link).
 */
public record CampaignTransactionLinkDto(
        UUID uuid,
        @Display(Display.Kind.ENUM) CampaignTransactionLink.LinkSource source,
        @Display(Display.Kind.DATETIME) OffsetDateTime resolvedAt,
        @Display DisplayRef payment,
        @Display DisplayRef membership,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount,
        String currency_Code) {
}
