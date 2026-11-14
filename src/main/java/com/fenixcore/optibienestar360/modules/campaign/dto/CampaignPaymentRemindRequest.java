package com.fenixcore.optibienestar360.modules.campaign.dto;

import jakarta.validation.constraints.Size;

/** Body of the campaign payment reminders (V260, hub ADR 0038 §9): an optional note added to the email. */
public record CampaignPaymentRemindRequest(@Size(max = 500) String note) {
}
