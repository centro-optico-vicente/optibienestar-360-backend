package com.fenixcore.optisaludplus.modules.payment.dto;

import java.time.Instant;

/**
 * Response body for {@code GET /v1/admin/payments/{uuid}/support}. Carries
 * a short-lived presigned URL the frontend can use to download (or open in
 * a new tab) the proof-of-payment object from Cloudflare R2.
 *
 * <p>Returned as JSON rather than a 302 redirect: SPA clients prefer to
 * inspect the URL (open in a new tab, copy, log) and 302 + Authorization
 * header have known interop issues across browsers.</p>
 */
public record PaymentSupportUrlDto(
        String url,
        Instant expiresAt,
        long expiresInSeconds,

        // Metadata also echoed so the frontend can render a "Download
        // proof.pdf (256 KB)" button without a second round-trip.
        String fileName,
        String contentType,
        Long sizeBytes
) {}
