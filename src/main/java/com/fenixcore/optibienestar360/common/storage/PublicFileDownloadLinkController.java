package com.fenixcore.optibienestar360.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Public (unauthenticated) redirect for shareable download links — spec §6.
 * The token is the secret; there is nothing else to authorize. Responds
 * with a {@code 302} to a freshly-minted short-lived presigned URL rather
 * than JSON, since this is opened directly from an email client, not the
 * SPA — there is no Authorization header to attach.
 *
 * <p>Mapped under {@code /v1/public/**} (already permitted in
 * {@code SecurityConfig.PUBLIC_PATHS}) rather than a new top-level path, to
 * avoid touching the security allow-list.</p>
 */
@RestController
@RequiredArgsConstructor
public class PublicFileDownloadLinkController {

    private final FileDownloadLinkService downloadLinkService;

    @GetMapping("/v1/public/files/links/{token}")
    public ResponseEntity<Void> resolve(@PathVariable UUID token) {
        try {
            String presignedUrl = downloadLinkService.resolvePresignedUrl(token);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(presignedUrl))
                    .build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.GONE).header(HttpHeaders.CONTENT_LENGTH, "0").build();
        }
    }
}
