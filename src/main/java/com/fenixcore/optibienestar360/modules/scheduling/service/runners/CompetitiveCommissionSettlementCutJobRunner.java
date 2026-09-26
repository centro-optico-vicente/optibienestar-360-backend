package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CompetitiveCommissionSettlementService;
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
 * {@code COMPETITIVE_COMMISSION_SETTLEMENT_CUT} (hub plan competitive-commission-rules, Fase 2b) —
 * the D14 settlement scheduler, replicating {@link CommissionRetroactiveSettlementCutJobRunner}'s
 * shape: a cheap, safe, zero-write call on a day with nothing due (the service only inserts a row
 * when its computed {@code amount > 0}), idempotent by {@code uq_ccas_cut}. Runs after the
 * evaluation job (§7) so awards are already confirmed by the time their cuts are considered.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompetitiveCommissionSettlementCutJobRunner implements ScheduledJobRunner {

    public static final String CODE = "COMPETITIVE_COMMISSION_SETTLEMENT_CUT";

    private final ScheduledJobRepository jobRepository;
    private final CompetitiveCommissionRuleRepository ruleRepository;
    private final CompetitiveCommissionSettlementService settlementService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        List<CompetitiveCommissionRule> rules = ruleRepository.findByActiveTrue();

        int rulesProcessed = 0, settlementsCreated = 0, settlementsSkippedZero = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        for (CompetitiveCommissionRule rule : rules) {
            try {
                var outcome = settlementService.executeCutForRule(rule.getUuid(), today, false);
                rulesProcessed++;
                settlementsCreated += outcome.settlementsCreated();
                settlementsSkippedZero += outcome.settlementsSkippedZero();
            } catch (RuntimeException ex) {
                log.error("COMPETITIVE_COMMISSION_SETTLEMENT_CUT failed for rule {}: {}", rule.getUuid(), ex.getMessage(), ex);
                Map<String, String> error = new HashMap<>();
                error.put("ruleUuid", rule.getUuid().toString());
                error.put("message", String.valueOf(ex.getMessage()));
                errors.add(error);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("rulesProcessed", rulesProcessed);
        summary.put("settlementsCreated", settlementsCreated);
        summary.put("settlementsSkippedZero", settlementsSkippedZero);
        summary.put("errors", errors);

        log.info("COMPETITIVE_COMMISSION_SETTLEMENT_CUT completed: rules={} created={} skippedZero={} errors={}",
                rulesProcessed, settlementsCreated, settlementsSkippedZero, errors.size());

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
