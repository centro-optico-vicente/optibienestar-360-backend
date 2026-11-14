package com.fenixcore.optibienestar360.modules.campaign.dto;

/** Result of {@code POST /v1/admin/campaigns/{uuid}/payments/remind-pending}: reminders sent, and skipped (reminded less than an hour ago). */
public record CampaignPaymentRemindResultDto(int sent, int skipped) {
}
