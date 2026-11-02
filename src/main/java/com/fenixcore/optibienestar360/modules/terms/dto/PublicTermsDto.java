package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;
import java.util.UUID;

/**
 * Anonymous-facing projection for {@code GET /v1/public/terms/{type}} —
 * only ever the current, {@code isPublic=true} version; no audit/status
 * fields, same sanitization criterion as {@code PublicPlanDto}.
 * {@code contentMarkdown} is already rendered (placeholders resolved, see
 * {@code TermsTemplateRenderer}); {@code uuid} is what the public
 * affiliation form sends back as {@code termsVersionUuid}. {@code segment} is
 * set only on ALIADO templates split by segment (B2B / B2P, hub ADR 0031).
 */
public record PublicTermsDto(
        UUID uuid,
        TermType termType,
        String title,
        String contentMarkdown,
        Instant validFrom,
        AllyType.Segment segment
) {

    /** A version that is not split by segment. */
    public PublicTermsDto(UUID uuid, TermType termType, String title, String contentMarkdown, Instant validFrom) {
        this(uuid, termType, title, contentMarkdown, validFrom, null);
    }
}
