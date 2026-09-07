package com.fenixcore.optibienestar360.core.util;

import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * The single source of truth for "the app's own timezone" (ADR 0010 —
 * Venezuela as primary market, {@code America/Caracas}, UTC-4 no DST).
 *
 * <p>Every container in {@code deployment/docker-compose.yaml} already sets
 * {@code TZ: "$TIME_ZONE"} (from {@code deployment/env_template.env}'s
 * {@code TIME_ZONE=America/Caracas}), so reading {@code TZ} directly here —
 * rather than every call site hardcoding {@code ZoneId.of("America/Caracas")}
 * — means changing the deployed timezone is a one-line env var edit, not a
 * multi-file code change. Falls back to the {@code America/Caracas} literal
 * when {@code TZ} is unset, blank, or not a valid zone id (e.g. running
 * outside the container, or a typo in the env — never let a bad/missing env
 * var silently shift every date computation in the app).</p>
 */
@Slf4j
public final class AppTimeZone {

    private static final ZoneId FALLBACK = ZoneId.of("America/Caracas");

    /** The app's business timezone — resolved once at class-load. See class javadoc. */
    public static final ZoneId ZONE = resolve();

    private AppTimeZone() {
    }

    private static ZoneId resolve() {
        String tz = System.getenv("TZ");
        if (tz != null && !tz.isBlank()) {
            try {
                return ZoneId.of(tz.trim());
            } catch (DateTimeException invalid) {
                log.warn("Invalid TZ env value '{}', falling back to {}", tz, FALLBACK);
            }
        }
        return FALLBACK;
    }
}
