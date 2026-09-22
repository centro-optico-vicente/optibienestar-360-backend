package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;
import java.util.UUID;

/** Full admin view of a version — {@code GET /v1/admin/terms} / {@code /{uuid}} / {@code /current/{type}}. */
public record TermsVersionDto(
        UUID uuid,
        TermType termType,
        String title,
        String contentMarkdown,
        boolean isPublic,
        Instant validFrom,
        boolean isVigent,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
