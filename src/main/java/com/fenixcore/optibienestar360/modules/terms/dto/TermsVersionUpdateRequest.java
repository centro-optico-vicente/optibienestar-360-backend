package com.fenixcore.optibienestar360.modules.terms.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Payload for {@code PUT /v1/admin/terms/{uuid}} — PATCH semantics, only
 * permitted while the row's {@code validFrom} is still in the future (see
 * {@code TermsVersionService.update}); once vigente it is immutable and a
 * new version must be created instead.
 */
public record TermsVersionUpdateRequest(
        @Size(max = 200) String title,
        String contentMarkdown,
        Boolean isPublic,
        Instant validFrom
) {}
