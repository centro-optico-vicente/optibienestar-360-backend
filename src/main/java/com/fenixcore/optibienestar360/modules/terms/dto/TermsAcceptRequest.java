package com.fenixcore.optibienestar360.modules.terms.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Payload for {@code POST /v1/me/terms/accept} — one UUID per checked box in the modal. */
public record TermsAcceptRequest(
        @NotEmpty List<UUID> termsVersionUuids
) {}
