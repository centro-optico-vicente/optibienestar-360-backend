package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.Kind;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.ReasonCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTie;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTieCandidate;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionManualDecisionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionTieRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * D16 (hub plan competitive-commission-rules, Fase 2c) — the coordinator's 3 actions over an
 * evaluated period: resolve an open tie, redirect a position or disqualify a winner, and revert
 * any of those. Each persists a {@link CompetitiveCommissionManualDecision} (or flips a tie's
 * status) and then re-runs {@link CompetitiveCommissionEvaluationService#evaluateRule} for the
 * same rule — which reads the fresh decision back as a pin/exclusion (§7) — followed by every
 * lower-{@code group_priority} sibling rule in the same {@code competition_group}, since their
 * eligible pool depends on who won here (D16's "cascade").
 *
 * <p>Mutations are persisted <em>before</em> triggering re-evaluation, since {@code evaluateRule}
 * runs in its own new transaction (§7) and would otherwise commit independently of this method's
 * own transaction outcome — ordering it last keeps that gap as small as it can be.</p>
 */
@Service
@RequiredArgsConstructor
public class CompetitiveWinnerDecisionService {

    private static final int MIN_REASON_LENGTH = 10;

    private final CompetitiveCommissionTieRepository tieRepository;
    private final CompetitiveCommissionManualDecisionRepository decisionRepository;
    private final CompetitiveCommissionRuleRepository ruleRepository;
    private final CompetitiveCommissionAwardRepository awardRepository;
    private final PromoterRepository promoterRepository;
    private final CompetitiveCommissionEvaluationService evaluationService;

    /** {@code winnerPromoterUuids.size()} must equal the tie's {@code slots}, all drawn from its candidates. */
    @Transactional
    public CompetitiveCommissionEvaluationService.EvaluationOutcome resolveTie(
            UUID tieUuid, List<UUID> winnerPromoterUuids, String reason, UUID decidedBy, boolean dryRun) {
        CompetitiveCommissionTie tie = tieRepository.findByUuid(tieUuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_tie.not_found"));
        if ("STALE".equals(tie.getStatus())) {
            throw new IllegalStateException("competitive_tie.stale");
        }
        if (!"OPEN".equals(tie.getStatus())) {
            throw new IllegalStateException("competitive_tie.already_resolved");
        }
        validateReason(reason);
        Set<UUID> candidateUuids = tie.getCandidates().stream()
                .map(c -> c.getPromoter().getUuid()).collect(Collectors.toSet());
        if (winnerPromoterUuids.size() != tie.getSlots() || !candidateUuids.containsAll(winnerPromoterUuids)
                || Set.copyOf(winnerPromoterUuids).size() != winnerPromoterUuids.size()) {
            throw new IllegalArgumentException("competitive_tie.invalid_selection");
        }
        for (UUID winnerUuid : winnerPromoterUuids) {
            guardNotPaid(tie.getRule(), tie.getPeriodStart(), winnerUuid);
        }

        if (dryRun) {
            return evaluationService.evaluateRule(tie.getRule().getUuid(), tie.getPeriodStart(), true);
        }

        tie.setStatus("RESOLVED");
        tie.setResolvedBy(decidedBy);
        tie.setResolvedAt(Instant.now());
        tie.setReason(reason);
        for (CompetitiveCommissionTieCandidate candidate : tie.getCandidates()) {
            candidate.setSelected(winnerPromoterUuids.contains(candidate.getPromoter().getUuid()));
        }

        int position = tie.getPositionFrom();
        for (UUID winnerUuid : winnerPromoterUuids) {
            CompetitiveCommissionManualDecision decision = new CompetitiveCommissionManualDecision();
            decision.setRule(tie.getRule());
            decision.setTie(tie);
            decision.setPeriodStart(tie.getPeriodStart());
            decision.setKind(Kind.TIE_RESOLUTION);
            decision.setAwardPosition(position++);
            decision.setPromoter(findPromoter(winnerUuid));
            decision.setReasonCategory(ReasonCategory.TIE_BREAK);
            decision.setReason(reason);
            decision.setDecidedBy(decidedBy);
            decisionRepository.save(decision);
        }

        var outcome = evaluationService.evaluateRule(tie.getRule().getUuid(), tie.getPeriodStart(), false);
        cascadeToLowerPrioritySiblings(tie.getRule(), tie.getPeriodStart());
        return outcome;
    }

    /**
     * {@code REDIRECT}: {@code promoterUuid} is the current holder losing the position,
     * {@code replacementPromoterUuid} the classified promoter taking it over (required, along
     * with {@code awardPosition}). {@code DISQUALIFY}: {@code promoterUuid} is excluded outright;
     * {@code excludeFromGroup} additionally bans them from every sibling rule of the same group.
     */
    @Transactional
    public CompetitiveCommissionEvaluationService.EvaluationOutcome decide(
            UUID ruleUuid, LocalDate periodStart, Kind kind, Integer awardPosition, UUID promoterUuid,
            UUID replacementPromoterUuid, boolean excludeFromGroup, ReasonCategory reasonCategory, String reason,
            UUID decidedBy, boolean dryRun) {
        validateReason(reason);
        CompetitiveCommissionRule rule = ruleRepository.findByUuid(ruleUuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_commission_rule.not_found"));
        guardNotPaid(rule, periodStart, promoterUuid);
        if (kind == Kind.REDIRECT) {
            if (replacementPromoterUuid == null || awardPosition == null) {
                throw new IllegalArgumentException("competitive_decision.invalid_replacement");
            }
            guardNotPaid(rule, periodStart, replacementPromoterUuid);
        }

        if (dryRun) {
            return evaluationService.evaluateRule(ruleUuid, periodStart, true);
        }

        CompetitiveCommissionManualDecision decision = new CompetitiveCommissionManualDecision();
        decision.setRule(rule);
        decision.setPeriodStart(periodStart);
        decision.setKind(kind);
        decision.setReasonCategory(reasonCategory != null ? reasonCategory : ReasonCategory.OTHER);
        decision.setReason(reason);
        decision.setDecidedBy(decidedBy);
        if (kind == Kind.REDIRECT) {
            decision.setAwardPosition(awardPosition);
            decision.setPromoter(findPromoter(replacementPromoterUuid));
            decision.setReplacedPromoter(findPromoter(promoterUuid));
        } else {
            decision.setPromoter(findPromoter(promoterUuid));
            decision.setExcludeFromGroup(excludeFromGroup);
        }
        decisionRepository.save(decision);

        var outcome = evaluationService.evaluateRule(ruleUuid, periodStart, false);
        cascadeToLowerPrioritySiblings(rule, periodStart);
        return outcome;
    }

    @Transactional
    public CompetitiveCommissionEvaluationService.EvaluationOutcome revert(UUID decisionUuid, String reason, UUID revertedBy) {
        validateReason(reason);
        CompetitiveCommissionManualDecision decision = decisionRepository.findByUuid(decisionUuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_decision.not_found"));
        if (!"ACTIVE".equals(decision.getStatus())) {
            throw new IllegalStateException("competitive_decision.not_active");
        }
        guardNotPaid(decision.getRule(), decision.getPeriodStart(), decision.getPromoter().getUuid());

        decision.setStatus("REVERTED");
        decision.setRevertedAt(Instant.now());
        decision.setRevertedBy(revertedBy);
        decision.setRevertReason(reason);

        var outcome = evaluationService.evaluateRule(decision.getRule().getUuid(), decision.getPeriodStart(), false);
        cascadeToLowerPrioritySiblings(decision.getRule(), decision.getPeriodStart());
        return outcome;
    }

    private void cascadeToLowerPrioritySiblings(CompetitiveCommissionRule rule, LocalDate periodStart) {
        if (rule.getCompetitionGroup() == null || rule.getGroupPriority() == null) {
            return;
        }
        List<CompetitiveCommissionRule> siblings = ruleRepository
                .findByCompetitionGroupAndActiveTrue(rule.getCompetitionGroup()).stream()
                .filter(s -> !s.getId().equals(rule.getId()) && s.getGroupPriority() != null
                        && s.getGroupPriority() > rule.getGroupPriority())
                .sorted(Comparator.comparing(CompetitiveCommissionRule::getGroupPriority))
                .toList();
        for (CompetitiveCommissionRule sibling : siblings) {
            evaluationService.evaluateRule(sibling.getUuid(), periodStart, false);
        }
    }

    private void guardNotPaid(CompetitiveCommissionRule rule, LocalDate periodStart, UUID promoterUuid) {
        Promoter promoter = findPromoter(promoterUuid);
        boolean paid = awardRepository
                .findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(rule.getId(), periodStart, "VOIDED").stream()
                .filter(a -> a.getPromoter().getId().equals(promoter.getId()))
                .anyMatch(a -> "PAID".equals(a.getStatus()));
        if (paid) {
            throw new IllegalStateException("competitive_award.already_paid");
        }
    }

    private Promoter findPromoter(UUID uuid) {
        return promoterRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new IllegalArgumentException("competitive_decision.reason_required");
        }
    }
}
