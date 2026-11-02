package com.fenixcore.optibienestar360.modules.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * One point of a dashboard series. {@code key} is a {@code yyyy-MM} month or an
 * enum code (labels resolved client-side); {@code label} is only sent for
 * literal data such as person or service names.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardChartPointDto(String key, String label, BigDecimal value) {

    public static DashboardChartPointDto of(String key, long value) {
        return new DashboardChartPointDto(key, null, BigDecimal.valueOf(value));
    }

    public static DashboardChartPointDto of(String key, BigDecimal value) {
        return new DashboardChartPointDto(key, null, value);
    }

    public static DashboardChartPointDto labelled(String key, String label, long value) {
        return new DashboardChartPointDto(key, label, BigDecimal.valueOf(value));
    }
}
