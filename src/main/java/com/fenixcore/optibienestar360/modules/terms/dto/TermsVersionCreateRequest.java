package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Payload for {@code POST /v1/admin/terms}. Lands as a new, independent row —
 * never mutates a previous one. {@code isPublic} defaults to {@code false}
 * when omitted (see V139 column default); pass it explicitly to publish
 * public right away.
 */
public record TermsVersionCreateRequest(
        @NotNull TermType termType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String contentMarkdown,
        Boolean isPublic,
        @NotNull Instant validFrom
) {}
