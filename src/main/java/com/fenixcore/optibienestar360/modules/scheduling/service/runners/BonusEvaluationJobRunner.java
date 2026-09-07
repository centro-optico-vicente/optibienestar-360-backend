package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusEvaluationService;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Monthly automatic bonus evaluation (v2 PDF #5). Resolved by code
 * {@code BONUS_EVALUATION} (seeded as a {@code scheduled_jobs} row by V37, cron
 * {@code 0 0 4 1 * *} America/Caracas — the 1st at 04:00, after the daily status
 * sweep so month-close active-subscriber counts are settled).
 *
 * <p>Delegates the whole computation to
 * {@link BonusEvaluationService#evaluate(LocalDate, boolean)} (which owns the
 * transaction) and summarizes the result into the job-run record. The engine is
 * idempotent, so a re-run of the same reference date grants nothing new.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BonusEvaluationJobRunner implements ScheduledJobRunner {

    public static final String CODE = "BONUS_EVALUATION";

    private final ScheduledJobRepository jobRepository;
    private final BonusEvaluationService bonusEvaluationService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        BonusEvaluationResponse result = bonusEvaluationService.evaluate(today, false);

        Map<String, Object> summary = new HashMap<>();
        summary.put("asOf", result.asOf().toString());
        summary.put("rulesEvaluated", result.rulesEvaluated());
        summary.put("awardsCreated", result.awardsCreated());
        summary.put("totalAmount", result.totalAmount());
        summary.put("currency", result.currency());

        log.info("BONUS_EVALUATION completed: asOf={} rules={} awards={} total={}",
                result.asOf(), result.rulesEvaluated(), result.awardsCreated(), result.totalAmount());
        return JobRunResult.success(summary);
    }

    /**
     * Reads the configured timezone from the {@code scheduled_jobs} row, falling
     * back to America/Caracas (ADR 0010) when the row is missing or malformed.
     */
    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} job row — falling back to America/Caracas",
                                job.getTimezone(), CODE);
                        return AppTimeZone.ZONE;
                    }
                })
                .orElse(AppTimeZone.ZONE);
    }
}
