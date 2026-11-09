package com.fenixcore.optibienestar360.modules.onboarding.dto;

import java.util.List;

/**
 * Progress of the caller's self-registration ({@code GET /v1/me/registration-progress},
 * hub ADR 0037 §3). {@code kind} is {@code MEMBER} or {@code ALLY}; {@code null}
 * (with no steps and {@code showCompletionPortal = false}) when the caller has
 * no self-registration. {@code confirmedByOperator} is true only after a manual
 * approval by staff; {@code showCompletionPortal} says whether the
 * "Completar registro" portal (route and menu entry) should exist.
 */
public record RegistrationProgressDto(
        String kind,
        List<Step> steps,
        int percent,
        boolean submitted,
        boolean confirmedByOperator,
        boolean showCompletionPortal
) {

    /** One step of the wizard: {@code essential} ones are required to submit. */
    public record Step(String key, boolean done, boolean essential) {}

    public static RegistrationProgressDto none() {
        return new RegistrationProgressDto(null, List.of(), 100, false, false, false);
    }
}
