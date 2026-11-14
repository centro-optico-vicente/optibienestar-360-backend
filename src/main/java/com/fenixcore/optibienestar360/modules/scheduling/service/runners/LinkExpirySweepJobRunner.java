package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.common.storage.LinkExpirySweepService;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hourly job (V249, seeded <b>enabled</b> since it only marks): flags download
 * links and report shares past their validity as {@code EXPIRED} through
 * {@link LinkExpirySweepService}. Nothing is deleted.
 */
@Component
@RequiredArgsConstructor
public class LinkExpirySweepJobRunner implements ScheduledJobRunner {

    public static final String CODE = "LINK_EXPIRY_SWEEP";

    private final LinkExpirySweepService sweepService;
    private final Clock clock;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        Instant now = Instant.now(clock);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("downloadLinks", sweepService.expireDownloadLinks(now));
        summary.put("reportShares", sweepService.expireReportShares(now));
        return JobRunResult.success(summary);
    }
}
