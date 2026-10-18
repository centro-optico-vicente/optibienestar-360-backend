package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/terms/{uuid}} — PATCH semantics, only
 * permitted while the row's {@code validFrom} is still in the future (see
 * {@code TermsVersionService.update}); once vigente it is immutable and a
 * new version must be created instead.
 *
 * <p>Scope fields (ALIADO only, V183) follow the same PATCH rule: a
 * non-null value sets that scope field, {@code null} leaves it unchanged.</p>
 */
public record TermsVersionUpdateRequest(
        @Size(max = 200) String title,
        String contentMarkdown,
        Boolean isPublic,
        Instant validFrom,
        UUID allyTypeUuid,
        Segment segment,
        UUID allyUuid
) {}
