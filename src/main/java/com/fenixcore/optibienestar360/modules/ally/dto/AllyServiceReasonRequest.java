package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reason-carrying payload for the negative workflow transitions
 * ({@code reject} and admin {@code remove}). The reason is mandatory — the V11
 * CHECK requires {@code review_reason} whenever the status becomes REJECTED or
 * REMOVED — and it is surfaced to the ally so they understand the decision.
 */
public record AllyServiceReasonRequest(
        @NotBlank @Size(max = 2000) String reason
) {}
