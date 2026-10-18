package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/terms}. Lands as a new, independent row —
 * never mutates a previous one. {@code isPublic} defaults to {@code false}
 * when omitted (see V139 column default); it only controls whether the public
 * web endpoint serves the version, never who must accept it.
 *
 * <p>{@code allyTypeUuid} / {@code segment} / {@code allyUuid} are the
 * optional scope of an ALIADO template (V183) — all omitted = general
 * template; any of them on another type is a 422
 * ({@code terms_version.scope.only_aliado}).</p>
 */
public record TermsVersionCreateRequest(
        @NotNull TermType termType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String contentMarkdown,
        Boolean isPublic,
        @NotNull Instant validFrom,
        UUID allyTypeUuid,
        Segment segment,
        UUID allyUuid
) {}
