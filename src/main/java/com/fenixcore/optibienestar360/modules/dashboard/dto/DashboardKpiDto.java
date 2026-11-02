package com.fenixcore.optibienestar360.modules.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * One indicator card of the home dashboard. The visible label is resolved by
 * the frontend from {@code key}; the backend only ships values.
 *
 * @param key           stable indicator key (e.g. {@code monthlyRevenue})
 * @param section       dashboard block the card belongs to
 * @param format        how {@code value} / {@code text} must be rendered
 * @param value         numeric value for NUMBER / MONEY / RATING; null when there is none
 * @param previousValue same indicator for the previous calendar month (drives the variation badge)
 * @param currency      ISO code, only for MONEY
 * @param text          TEXT: an enum code translated client-side; DATE: an ISO {@code yyyy-MM-dd} date
 * @param count         RATING: number of ratings behind the average
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardKpiDto(
        String key,
        DashboardSection section,
        Format format,
        BigDecimal value,
        BigDecimal previousValue,
        String currency,
        String text,
        Long count
) {

    public enum Format { NUMBER, MONEY, TEXT, DATE, RATING }

    public static DashboardKpiDto number(DashboardSection section, String key, long value) {
        return new DashboardKpiDto(key, section, Format.NUMBER, BigDecimal.valueOf(value), null, null, null, null);
    }

    public static DashboardKpiDto number(DashboardSection section, String key, long value, long previous) {
        return new DashboardKpiDto(key, section, Format.NUMBER, BigDecimal.valueOf(value),
                BigDecimal.valueOf(previous), null, null, null);
    }

    public static DashboardKpiDto money(DashboardSection section, String key, BigDecimal value,
                                        BigDecimal previous, String currency) {
        return new DashboardKpiDto(key, section, Format.MONEY, value, previous, currency, null, null);
    }

    public static DashboardKpiDto text(DashboardSection section, String key, String code) {
        return new DashboardKpiDto(key, section, Format.TEXT, null, null, null, code, null);
    }

    /** {@code isoDate} null means "none" (e.g. no upcoming installment). */
    public static DashboardKpiDto date(DashboardSection section, String key, String isoDate) {
        return new DashboardKpiDto(key, section, Format.DATE, null, null, null, isoDate, null);
    }

    public static DashboardKpiDto rating(DashboardSection section, String key, BigDecimal average, long count) {
        return new DashboardKpiDto(key, section, Format.RATING, average, null, null, null, count);
    }
}
