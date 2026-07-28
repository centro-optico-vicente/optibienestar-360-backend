package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.promoter.dto.PrizeAwardResult;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardPrizeService;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatic leaderboard prize awarding at period close (v2 PDF #5). Resolved by
 * code {@code LEADERBOARD_PRIZE_AWARD} (seeded as a {@code scheduled_jobs} row by
 * V42, cron {@code 0 0 5 1 * *} America/Caracas — the 1st at 05:00, after the
 * status sweep and the bonus evaluation so month-close commission totals are
 * settled).
 *
 * <p>Delegates to {@link LeaderboardPrizeService#awardClosedPeriods(LocalDate, boolean)}
 * which grants prizes only for periods that fully closed yesterday and is
 * idempotent, so a re-run grants nothing new.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardPrizeJobRunner implements ScheduledJobRunner {

    public static final String CODE = "LEADERBOARD_PRIZE_AWARD";

    private final ScheduledJobRepository jobRepository;
    private final LeaderboardPrizeService prizeService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        List<PrizeAwardResult> results = prizeService.awardClosedPeriods(today, false);

        int awardsCreated = results.stream().mapToInt(PrizeAwardResult::awardsCreated).sum();
        BigDecimal total = results.stream()
                .map(PrizeAwardResult::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new HashMap<>();
        summary.put("runDate", today.toString());
        summary.put("periodsClosed", results.size());
        summary.put("awardsCreated", awardsCreated);
        summary.put("totalAmount", total);

        log.info("LEADERBOARD_PRIZE_AWARD completed: runDate={} periodsClosed={} awards={} total={}",
                today, results.size(), awardsCreated, total);
        return JobRunResult.success(summary);
    }

    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} job row — falling back to America/Caracas",
                                job.getTimezone(), CODE);
                        return ZoneId.of("America/Caracas");
                    }
                })
                .orElse(ZoneId.of("America/Caracas"));
    }
}
