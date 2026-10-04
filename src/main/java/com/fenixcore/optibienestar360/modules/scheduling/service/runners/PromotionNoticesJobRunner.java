package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.ScheduledJobParams;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionNoticeService;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/** Daily job {@code PROMOTION_NOTICES} (V176): promotion reminders ahead of the next payment. */
@Component
@RequiredArgsConstructor
public class PromotionNoticesJobRunner implements ScheduledJobRunner {

    public static final String CODE = "PROMOTION_NOTICES";
    private static final int DEFAULT_DAYS_BEFORE_DUE = 5;

    private final ScheduledJobRepository jobRepository;
    private final PromotionNoticeService noticeService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        int daysBeforeDue = jobRepository.findByCode(CODE)
                .map(job -> ScheduledJobParams.intParam(job.getParameters(), "daysBeforeDue", DEFAULT_DAYS_BEFORE_DUE))
                .orElse(DEFAULT_DAYS_BEFORE_DUE);
        int sent = noticeService.sendDueNotices(LocalDate.now(AppTimeZone.ZONE), daysBeforeDue);
        return JobRunResult.success(Map.of("sent", sent, "daysBeforeDue", daysBeforeDue));
    }
}
