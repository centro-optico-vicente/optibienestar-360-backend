package com.fenixcore.optibienestar360.modules.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Body of {@code GET /v1/me/dashboard}: the home panel of the intranet, built
 * from the caller's authorities. {@code sections} lists the blocks that apply
 * (in display order); KPIs and charts carry their own section, and
 * {@code activity} is the merged feed of every section, newest first.
 *
 * @param periodStart first day of the current month in the app timezone
 */
public record DashboardDto(
        LocalDate periodStart,
        List<DashboardSection> sections,
        List<DashboardKpiDto> kpis,
        List<DashboardChartDto> charts,
        List<DashboardActivityDto> activity
) {}
