package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CompetitiveCommissionEvaluationService;
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
 * {@code COMPETITIVE_COMMISSION_EVALUATION} (hub plan competitive-commission-rules, Fase 2b):
 * every 15 minutes, evaluates every active competitive rule for today's accrual window and
 * confirms whatever's past its {@code confirmationDelayDays}. Each rule runs in its own
 * transaction ({@code CompetitiveCommissionEvaluationService}'s own {@code REQUIRES_NEW}), so one
 * rule's failure can never abort the others — collected into {@code errors} instead. Only
 * {@code FAILED} outright when every single rule failed.
 *
 * <p>A manual, single-rule run (preview or apply) goes through {@code
 * AdminCompetitiveCommissionRuleController#recalculate} instead — this runner always evaluates
 * every active rule with {@code dryRun=false}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompetitiveCommissionEvaluationJobRunner implements ScheduledJobRunner {

    public static final String CODE = "COMPETITIVE_COMMISSION_EVALUATION";

    private final ScheduledJobRepository jobRepository;
    private final CompetitiveCommissionRuleRepository ruleRepository;
    private final CompetitiveCommissionEvaluationService evaluationService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        List<CompetitiveCommissionRule> rules = ruleRepository.findByActiveTrue();

        int rulesProcessed = 0, awardsCreated = 0, awardsUpdated = 0, awardsDisplaced = 0, awardsConfirmed = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        for (CompetitiveCommissionRule rule : rules) {
            try {
                var outcome = evaluationService.evaluateRule(rule.getUuid(), today, false);
                rulesProcessed++;
                awardsCreated += outcome.created();
                awardsUpdated += outcome.updated();
                awardsDisplaced += outcome.displaced();
                awardsConfirmed += evaluationService.confirmDuePeriods(rule.getUuid(), today);
            } catch (RuntimeException ex) {
                log.error("COMPETITIVE_COMMISSION_EVALUATION failed for rule {}: {}", rule.getUuid(), ex.getMessage(), ex);
                Map<String, String> error = new HashMap<>();
                error.put("ruleUuid", rule.getUuid().toString());
                error.put("message", String.valueOf(ex.getMessage()));
                errors.add(error);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("rulesProcessed", rulesProcessed);
        summary.put("rulesSkipped", rules.size() - rulesProcessed - errors.size());
        summary.put("awardsCreated", awardsCreated);
        summary.put("awardsUpdated", awardsUpdated);
        summary.put("awardsDisplaced", awardsDisplaced);
        summary.put("awardsConfirmed", awardsConfirmed);
        summary.put("dryRun", false);
        summary.put("errors", errors);

        log.info("COMPETITIVE_COMMISSION_EVALUATION completed: rules={} created={} updated={} displaced={} confirmed={} errors={}",
                rulesProcessed, awardsCreated, awardsUpdated, awardsDisplaced, awardsConfirmed, errors.size());

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
