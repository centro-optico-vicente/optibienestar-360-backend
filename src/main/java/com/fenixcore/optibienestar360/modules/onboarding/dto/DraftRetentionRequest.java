package com.fenixcore.optibienestar360.modules.onboarding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Holds a registration draft out of the abandoned-draft cleanup ({@code retain = true}) or releases it
 * ({@code retain = false}). {@code note} is an optional reason (max 200) and is ignored when releasing.
 */
public record DraftRetentionRequest(
        @NotNull Boolean retain,
        @Size(max = 200) String note
) {}
