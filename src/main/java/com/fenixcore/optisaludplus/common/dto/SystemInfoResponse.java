package com.fenixcore.optisaludplus.common.dto;

import java.time.Instant;

/**
 * Lightweight metadata exposed at {@code GET /} and {@code GET /v1}.
 *
 * <p>{@code version} and {@code versionDate} are populated from the
 * {@code VERSION} / {@code VERSION_DATE} env vars baked into the Docker image
 * at build time from the GitHub release. {@code timestamp} is the server's
 * current time at request time, useful as a quick clock-sync / liveness probe.
 */
public record SystemInfoResponse(
        String application,
        String version,
        String versionDate,
        String profile,
        Instant timestamp
) {}
