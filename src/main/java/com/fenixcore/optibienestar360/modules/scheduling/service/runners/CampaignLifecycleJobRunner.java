package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.campaign.service.CampaignLifecycleService;
import com.fenixcore.optibienestar360.modules.promotion.service.MembershipBenefitGrantService;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionRedemptionService;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Daily job {@code CAMPAIGN_LIFECYCLE} (V256, hub ADR 0038): closes the
 * campaigns whose end date passed when END_DATE is one of their close
 * conditions. The promotion queries already stop offering a campaign past its
 * end before this runs. Since V257 it also expires the PENDING slots past
 * their deadline (a SOLD_OUT campaign that gets room back returns to ACTIVE)
 * and ends the promotions whose usage window is over. Since V257 it also
 * expires the ally vouchers not redeemed before their expiry.
 */
@Component
@RequiredArgsConstructor
public class CampaignLifecycleJobRunner implements ScheduledJobRunner {

    public static final String CODE = "CAMPAIGN_LIFECYCLE";

    private final CampaignLifecycleService lifecycleService;
    private final PromotionRedemptionService redemptionService;
    private final MembershipBenefitGrantService grantService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        int closed = lifecycleService.closeEnded(OffsetDateTime.now());
        Instant now = Instant.now();
        int expired = redemptionService.expirePending(now);
        int usageEnded = redemptionService.endUsageWindows(now);
        int grantsExpired = grantService.expireAvailable(now);
        return JobRunResult.success(Map.of(
                "closedByEndDate", closed,
                "expiredPending", expired,
                "usageWindowsEnded", usageEnded,
                "benefitGrantsExpired", grantsExpired));
    }
}
