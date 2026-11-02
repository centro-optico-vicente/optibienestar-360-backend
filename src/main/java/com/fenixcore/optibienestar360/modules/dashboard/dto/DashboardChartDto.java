package com.fenixcore.optibienestar360.modules.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One chart of the home dashboard; the title is resolved by the frontend from
 * {@code key}.
 *
 * @param labelKind how to label each point: MONTH ({@code key = yyyy-MM}),
 *                  ENUM (translate {@code key}) or TEXT (use {@code label} verbatim)
 * @param format    NUMBER or MONEY (then {@code currency} is set)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardChartDto(
        String key,
        DashboardSection section,
        Type type,
        LabelKind labelKind,
        DashboardKpiDto.Format format,
        String currency,
        List<DashboardChartPointDto> points
) {

    public enum Type { BAR, LINE, DONUT }

    public enum LabelKind { MONTH, ENUM, TEXT }

    public static DashboardChartDto counts(DashboardSection section, String key, Type type, LabelKind labelKind,
                                           List<DashboardChartPointDto> points) {
        return new DashboardChartDto(key, section, type, labelKind, DashboardKpiDto.Format.NUMBER, null, points);
    }
}
