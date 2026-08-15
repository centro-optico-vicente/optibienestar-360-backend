package com.fenixcore.optibienestar360.common.storage.dto;

import java.time.Instant;

/**
 * On-demand presigned download URL — generic sibling of
 * {@code PaymentSupportUrlDto}, returned as JSON (not a 302) so SPA clients
 * can inspect/open it without Authorization-header interop issues.
 */
public record FileUrlDto(
        String url,
        Instant expiresAt,
        long expiresInSeconds,
        String fileName,
        String contentType,
        Long sizeBytes
) {}
