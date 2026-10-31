package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * One opening-hours range of an ally (hub ADR 0031 §7). {@code dayOfWeek} is
 * ISO-8601 (1 = Monday … 7 = Sunday); {@code dayOfWeek_Display} is the
 * localized day name (ADR 0014). Times are {@code HH:mm} in the business zone.
 */
public record OpeningHourDto(
        int dayOfWeek,
        String dayOfWeek_Display,
        @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
        @JsonFormat(pattern = "HH:mm") LocalTime closesAt
) {}
