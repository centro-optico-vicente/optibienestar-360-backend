package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simple JSON effectiveness summary for a campaign — total collected vs
 * {@code targetAmount}, transaction count vs {@code targetCount}, both as a
 * percentage when a target is set (null target = no percentage to compute).
 *
 * <p>FOLLOW-UP: this intentionally does NOT go through the PDF/XLSX/CSV
 * reporting engine (ADR 0012, {@code modules/document/generic}) — the
 * campaign-to-report-row mapping wasn't confirmed against that engine's
 * generic-record contract within this pass's budget. A future pass should
 * wire this same aggregation into {@code GenericRecordReportService}/
 * {@code GenericXlsxExporterService} so campaigns get the same
 * download/attach/presigned-link delivery as the rest of the reporting
 * surface, instead of this bespoke JSON endpoint.</p>
 */
public record CampaignEffectivenessDto(
        UUID campaignUuid,
        String campaignName,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal totalCollected,
        long transactionCount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal targetAmount,
        Integer targetCount,
        BigDecimal amountAchievedPct,
        BigDecimal countAchievedPct,
        /** Currency both {@code totalCollected} and {@code targetAmount} were converted/expressed into — see {@link com.fenixcore.optibienestar360.modules.campaign.service.CampaignService#effectiveness}. */
        @Display DisplayRef currency,
        String currency_Code) {
}
