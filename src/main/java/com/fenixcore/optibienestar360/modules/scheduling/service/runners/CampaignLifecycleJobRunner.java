package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.campaign.service.CampaignLifecycleService;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Daily job {@code CAMPAIGN_LIFECYCLE} (V256, hub ADR 0038): closes the
 * campaigns whose end date passed when END_DATE is one of their close
 * conditions. The promotion queries already stop offering a campaign past its
 * end before this runs.
 */
@Component
@RequiredArgsConstructor
public class CampaignLifecycleJobRunner implements ScheduledJobRunner {

    public static final String CODE = "CAMPAIGN_LIFECYCLE";

    private final CampaignLifecycleService lifecycleService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        int closed = lifecycleService.closeEnded(OffsetDateTime.now());
        return JobRunResult.success(Map.of("closedByEndDate", closed));
    }
}
