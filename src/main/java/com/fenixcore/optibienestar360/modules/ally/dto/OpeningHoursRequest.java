package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/**
 * Replaces an ally's whole week of opening hours (hub ADR 0031 §7). An empty
 * list clears it. Ranges of the same day must not overlap and each must close
 * after it opens (422 otherwise).
 */
public record OpeningHoursRequest(
        @NotNull @Size(max = 50) List<@Valid @NotNull Range> ranges
) {
    public record Range(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closesAt
    ) {}
}
