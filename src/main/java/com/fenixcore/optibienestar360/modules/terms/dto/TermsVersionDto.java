package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;
import java.util.UUID;

/**
 * Full admin view of a version — {@code GET /v1/admin/terms} / {@code /{uuid}} / {@code /current/{type}}
 * and {@code GET /v1/admin/allies/{allyUuid}/applicable-terms}.
 * {@code contentMarkdown} is the raw stored template (what the editor edits);
 * {@code contentRendered} is the same text with placeholders resolved, for preview.
 *
 * <p>ALIADO scope (V183, all {@code null} = general template) follows ADR 0014:
 * {@code allyType_Uuid} + {@code allyType_Display}, {@code segment} +
 * {@code segment_Display}, {@code ally_Uuid} + {@code ally_Display}.
 * {@code isPublic} only means "served by the public web endpoint".</p>
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
        @Display DisplayRef allyType,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
        @Display DisplayRef ally,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
