package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingResponse.PromoterReRatingOutcome;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Month-close retroactive re-rating (vertical-8 Ítem A). {@link CommissionService}
 * prices each INSCRIPTION commission at approval time against the band the
 * promoter's running count qualifies for <i>that day</i> — so a promoter who
 * ends the month at 60 inscriptions may have paid a mix of 25%/30% depending
 * on when each payment was approved. This service re-rates every PENDING
 * INSCRIPTION commission in a closed period to the single highest band the
 * promoter's <i>total</i> monthly count reached, so 60 inscriptions all pay
 * 30% ($450), never a progressive blend.
 *
 * <p>Only touches {@code status = 'PENDING'} — a commission already
 * {@code PAID} is settled and out of scope (same "no retroactive rewrite of
 * settled money" rule as the permanent promoter link, PDF 2.a).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionReRatingService {

    private final CommissionRepository commissionRepository;
    private final CommissionTierRepository tierRepository;
    private final MemberRepository memberRepository;
    private final CommissionAuditRecorder auditRecorder;

    @Transactional
    public CommissionReRatingResponse execute(CommissionReRatingRequest request) {
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());

        List<Commission> pendingInscriptions = commissionRepository
                .findPendingForPeriod(request.periodStart(), request.periodEnd()).stream()
                .filter(c -> c.getAppliesTo() == AppliesTo.INSCRIPTION)
                .toList();

        Map<Long, List<Commission>> byPromoter = pendingInscriptions.stream()
                .collect(Collectors.groupingBy(c -> c.getPromoter().getId()));

        List<PromoterReRatingOutcome> outcomes = new ArrayList<>();
        int commissionsUpdated = 0;
        BigDecimal totalDelta = BigDecimal.ZERO;

        for (Map.Entry<Long, List<Commission>> entry : byPromoter.entrySet()) {
            List<Commission> rows = entry.getValue();
            Promoter promoter = rows.get(0).getPromoter();

            long count = memberRepository.countNewSubscribersForPromoter(
                    promoter.getId(), request.periodStart(), request.periodEnd());
            CommissionTier target = highestQualifyingTier(count, promoter);
            if (target == null) {
                log.warn("Re-rating skipped for promoter {}: no applicable INSCRIPTION volume tier", promoter.getReferralCode());
                continue;
            }

            BigDecimal promoterDelta = BigDecimal.ZERO;
            int promoterChanged = 0;
            for (Commission c : rows) {
                if (target.getId().equals(c.getCommissionTierId())) {
                    continue;   // already at the target band — no-op, re-runs stay idempotent
                }
                BigDecimal newAmount = c.getCalculationBasis()
                        .multiply(target.getCommissionPct())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                promoterDelta = promoterDelta.add(newAmount.subtract(c.getAmount()));
                promoterChanged++;
                if (!dryRun) {
                    Map<String, Object> before = auditRecorder.snapshot(c);
                    c.setCommissionPct(target.getCommissionPct());
                    c.setFlatAmount(null);
                    c.setAmount(newAmount);
                    c.setCommissionTierId(target.getId());
                    c.setTierNameSnapshot(target.getName());
                    auditRecorder.recordUpdate(c.getUuid(), before, auditRecorder.snapshot(c));
                }
            }

            commissionsUpdated += promoterChanged;
            totalDelta = totalDelta.add(promoterDelta);
            outcomes.add(new PromoterReRatingOutcome(
                    promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName(),
                    count, target.getUuid(), target.getName(), promoterChanged, promoterDelta));
        }

        log.info("COMMISSION_RE_RATING period={}..{} dryRun={} promoters={} commissionsUpdated={} delta={}",
                request.periodStart(), request.periodEnd(), dryRun, outcomes.size(), commissionsUpdated, totalDelta);

        return new CommissionReRatingResponse(request.periodStart(), request.periodEnd(), dryRun,
                outcomes.size(), commissionsUpdated, totalDelta, "USD", Instant.now(), outcomes);
    }

    /**
     * Highest unscoped INSCRIPTION tier (V49 volume scale) the promoter's
     * monthly count qualifies for — same "iterate highest-threshold-first,
     * first qualifying wins" rule as {@code CommissionService.selectTier},
     * scoped by promoter type (V46) the same way.
     */
    private CommissionTier highestQualifyingTier(long count, Promoter promoter) {
        Long promoterTypeId = promoter.getPromoterType() != null ? promoter.getPromoterType().getId() : null;
        List<CommissionTier> candidates = tierRepository.findActiveApplicable(
                null, CommissionTier.AppliesTo.INSCRIPTION, CommissionTier.AppliesTo.BOTH, promoterTypeId);
        for (CommissionTier tier : candidates) {
            if (tier.getThresholdCount() <= 0 || count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }
}
