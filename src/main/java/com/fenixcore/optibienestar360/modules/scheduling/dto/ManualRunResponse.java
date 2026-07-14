package com.fenixcore.optibienestar360.modules.scheduling.dto;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP body for {@code POST /v1/admin/scheduled-jobs/{uuid}/run-now}. The
 * controller maps this to either HTTP 200 (the sync path completed within
 * {@code max_sync_seconds}) or HTTP 202 (the runner is still going; the
 * frontend should poll {@link #statusUrl}).
 *
 * <p>In the sync 200 case: {@link #outcome} is the terminal value (SUCCESS
 * or FAILED), {@link #summary} carries the runner payload, and
 * {@link #statusUrl} is {@code null}.</p>
 *
 * <p>In the async 202 case: {@link #outcome} is {@code "RUNNING"},
 * {@link #summary} is {@code null}, {@link #errorMessage} is {@code null},
 * and {@link #statusUrl} points to the single-run polling endpoint.</p>
 */
public record ManualRunResponse(
        UUID runUuid,
        String outcome,
        Map<String, Object> summary,
        String errorMessage,
        String statusUrl
) {}
