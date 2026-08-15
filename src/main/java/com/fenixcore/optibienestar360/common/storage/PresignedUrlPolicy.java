package com.fenixcore.optibienestar360.common.storage;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Shared TTL bounds for on-demand presigned download URLs — extracted from
 * {@code PaymentsService} (spec §2) so every consumer (member/ally
 * documents, catalog images, and payments itself) clamps the same way
 * instead of re-declaring the constants.
 */
@Component
public class PresignedUrlPolicy {

    public static final Duration MIN_TTL = Duration.ofMinutes(1);
    public static final Duration MAX_TTL = Duration.ofHours(1);
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    public Duration clamp(Duration requested) {
        if (requested == null) return DEFAULT_TTL;
        if (requested.compareTo(MIN_TTL) < 0) return MIN_TTL;
        if (requested.compareTo(MAX_TTL) > 0) return MAX_TTL;
        return requested;
    }
}
