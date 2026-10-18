package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;
import java.util.UUID;

/**
 * Full admin view of a version — {@code GET /v1/admin/terms} / {@code /{uuid}} / {@code /current/{type}}.
 * {@code contentMarkdown} is the raw stored template (what the editor edits);
 * {@code contentRendered} is the same text with placeholders resolved, for preview.
 */
public record TermsVersionDto(
        UUID uuid,
        TermType termType,
        String title,
        String contentMarkdown,
        String contentRendered,
        boolean isPublic,
        Instant validFrom,
        boolean isVigent,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
