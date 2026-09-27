package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusSettlementCutService;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code BONUS_SETTLEMENT_CUT} (hub plan competitive-commission-rules §12,
 * V168) — replicates {@link
 * com.fenixcore.optibienestar360.modules.scheduling.service.runners.CompetitiveCommissionSettlementCutJobRunner}'s
 * shape: a cheap, safe, zero-write call on a day with nothing due (the
 * service only writes a row when its computed amount is {@code > 0}),
 * idempotent by {@code uq_promoter_bonus_awards_cut}. Absorbs the retired
 * {@code BONUS_EVALUATION} job (disabled by V168, not deleted) — this now
 * owns every bonus grant, on whatever cadence each rule's own settlement axes
 * resolve to instead of a fixed monthly cron.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BonusSettlementCutJobRunner implements ScheduledJobRunner {

    public static final String CODE = "BONUS_SETTLEMENT_CUT";

    private final ScheduledJobRepository jobRepository;
    private final CommissionBonusRuleRepository ruleRepository;
    private final BonusSettlementCutService settlementCutService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        List<CommissionBonusRule> rules = ruleRepository.findByActiveTrue();

        int rulesProcessed = 0, granted = 0, skippedZero = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        for (CommissionBonusRule rule : rules) {
            try {
                var outcome = settlementCutService.executeCutForRule(rule.getUuid(), today, false);
                rulesProcessed++;
                granted += outcome.granted();
                skippedZero += outcome.skippedZero();
            } catch (RuntimeException ex) {
                log.error("BONUS_SETTLEMENT_CUT failed for rule {}: {}", rule.getUuid(), ex.getMessage(), ex);
                Map<String, String> error = new HashMap<>();
                error.put("ruleUuid", rule.getUuid().toString());
                error.put("message", String.valueOf(ex.getMessage()));
                errors.add(error);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("rulesProcessed", rulesProcessed);
        summary.put("granted", granted);
        summary.put("skippedZero", skippedZero);
        summary.put("errors", errors);

        log.info("BONUS_SETTLEMENT_CUT completed: rules={} granted={} skippedZero={} errors={}",
                rulesProcessed, granted, skippedZero, errors.size());

        if (!rules.isEmpty() && errors.size() == rules.size()) {
            return JobRunResult.failure("all rules failed", summary);
        }
        return JobRunResult.success(summary);
    }

    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} job row — falling back to {}",
                                job.getTimezone(), CODE, AppTimeZone.ZONE);
                        return AppTimeZone.ZONE;
                    }
                })
                .orElse(AppTimeZone.ZONE);
    }
}
