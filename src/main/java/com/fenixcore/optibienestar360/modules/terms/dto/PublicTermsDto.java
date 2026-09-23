package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;

/**
 * Anonymous-facing projection for {@code GET /v1/public/terms/{type}} —
 * only ever the current, {@code isPublic=true} version; no audit/status
 * fields, same sanitization criterion as {@code PublicPlanDto}.
 */
public record PublicTermsDto(
        TermType termType,
        String title,
        String contentMarkdown,
        Instant validFrom
) {}
