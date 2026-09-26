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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitiveWinnerDecisionServiceTest {

    @Mock private CompetitiveCommissionTieRepository tieRepository;
    @Mock private CompetitiveCommissionManualDecisionRepository decisionRepository;
    @Mock private CompetitiveCommissionRuleRepository ruleRepository;
    @Mock private CompetitiveCommissionAwardRepository awardRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private CompetitiveCommissionEvaluationService evaluationService;

    private CompetitiveWinnerDecisionService sut() {
        return new CompetitiveWinnerDecisionService(
                tieRepository, decisionRepository, ruleRepository, awardRepository, promoterRepository, evaluationService);
    }

    private static CompetitiveCommissionRule rule() {
        CompetitiveCommissionRule rule = new CompetitiveCommissionRule();
        rule.setId(1L);
        rule.setUuid(UUID.randomUUID());
        return rule;
    }

    private static Promoter promoter(long id, UUID uuid) {
        Promoter promoter = new Promoter();
        promoter.setId(id);
        promoter.setUuid(uuid);
        return promoter;
    }

    private static CompetitiveCommissionTie openTie(CompetitiveCommissionRule rule, int slots, Promoter... candidates) {
        CompetitiveCommissionTie tie = new CompetitiveCommissionTie();
        tie.setUuid(UUID.randomUUID());
        tie.setRule(rule);
        tie.setPeriodStart(LocalDate.of(2026, 9, 1));
        tie.setPeriodEnd(LocalDate.of(2026, 9, 30));
        tie.setPositionFrom(1);
        tie.setSlots(slots);
        tie.setStatus("OPEN");
        for (Promoter promoter : candidates) {
            CompetitiveCommissionTieCandidate candidate = new CompetitiveCommissionTieCandidate();
            candidate.setTie(tie);
            candidate.setPromoter(promoter);
            candidate.setMetricValue(java.math.BigDecimal.TEN);
            candidate.setMetricTransactionCount(1);
            tie.getCandidates().add(candidate);
        }
        return tie;
    }

    @Test
    void resolveTie_persistsOneDecisionPerWinner_andReEvaluates() {
        CompetitiveCommissionRule rule = rule();
        Promoter winner = promoter(1L, UUID.randomUUID());
        Promoter loser = promoter(2L, UUID.randomUUID());
        CompetitiveCommissionTie tie = openTie(rule, 1, winner, loser);
        when(tieRepository.findByUuid(tie.getUuid())).thenReturn(Optional.of(tie));
        when(promoterRepository.findByUuid(winner.getUuid())).thenReturn(Optional.of(winner));
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());

        sut().resolveTie(tie.getUuid(), List.of(winner.getUuid()), "el coordinador eligió por antigüedad",
                UUID.randomUUID(), false);

        assertThat(tie.getStatus()).isEqualTo("RESOLVED");
        ArgumentCaptor<CompetitiveCommissionManualDecision> captor = ArgumentCaptor.forClass(CompetitiveCommissionManualDecision.class);
        verify(decisionRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(Kind.TIE_RESOLUTION);
        assertThat(captor.getValue().getAwardPosition()).isEqualTo(1);
        assertThat(captor.getValue().getPromoter()).isEqualTo(winner);
        verify(evaluationService).evaluateRule(rule.getUuid(), tie.getPeriodStart(), false);
    }

    @Test
    void resolveTie_dryRun_neverPersists() {
        CompetitiveCommissionRule rule = rule();
        Promoter winner = promoter(1L, UUID.randomUUID());
        CompetitiveCommissionTie tie = openTie(rule, 1, winner);
        when(tieRepository.findByUuid(tie.getUuid())).thenReturn(Optional.of(tie));
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());

        sut().resolveTie(tie.getUuid(), List.of(winner.getUuid()), "motivo suficientemente largo",
                UUID.randomUUID(), true);

        assertThat(tie.getStatus()).isEqualTo("OPEN");
        verify(decisionRepository, never()).save(any());
        verify(evaluationService).evaluateRule(rule.getUuid(), tie.getPeriodStart(), true);
    }

    @Test
    void resolveTie_wrongSelectionSize_rejected() {
        CompetitiveCommissionTie tie = openTie(rule(), 1, promoter(1L, UUID.randomUUID()), promoter(2L, UUID.randomUUID()));
        when(tieRepository.findByUuid(tie.getUuid())).thenReturn(Optional.of(tie));

        assertThatThrownBy(() -> sut().resolveTie(tie.getUuid(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), "motivo suficientemente largo", UUID.randomUUID(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_tie.invalid_selection");
    }

    @Test
    void resolveTie_candidateNotInTie_rejected() {
        Promoter candidate = promoter(1L, UUID.randomUUID());
        CompetitiveCommissionTie tie = openTie(rule(), 1, candidate);
        when(tieRepository.findByUuid(tie.getUuid())).thenReturn(Optional.of(tie));

        assertThatThrownBy(() -> sut().resolveTie(tie.getUuid(),
                List.of(UUID.randomUUID()), "motivo suficientemente largo", UUID.randomUUID(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_tie.invalid_selection");
    }

    @Test
    void resolveTie_reasonTooShort_rejected() {
        CompetitiveCommissionTie tie = openTie(rule(), 1, promoter(1L, UUID.randomUUID()));
        when(tieRepository.findByUuid(tie.getUuid())).thenReturn(Optional.of(tie));

        assertThatThrownBy(() -> sut().resolveTie(tie.getUuid(), List.of(UUID.randomUUID()), "corto", UUID.randomUUID(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_decision.reason_required");
    }

    @Test
    void resolveTie_stale_rejected() {
        CompetitiveCommissionTie tie = openTie(rule(), 1, promoter(1L, UUID.randomUUID()));
        tie.setStatus("STALE");
        when(tieRepository.findByUuid(tie.getUuid())).thenReturn(Optional.of(tie));

        assertThatThrownBy(() -> sut().resolveTie(tie.getUuid(), List.of(UUID.randomUUID()),
                "motivo suficientemente largo", UUID.randomUUID(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("competitive_tie.stale");
    }

    @Test
    void resolveTie_winnerAlreadyPaidElsewhereInRule_rejected() {
        CompetitiveCommissionRule rule = rule();
        Promoter winner = promoter(1L, UUID.randomUUID());
        CompetitiveCommissionTie tie = openTie(rule, 1, winner);
        when(tieRepository.findByUuid(tie.getUuid())).thenReturn(Optional.of(tie));
        when(promoterRepository.findByUuid(winner.getUuid())).thenReturn(Optional.of(winner));
        CompetitiveCommissionAward paidAward = new CompetitiveCommissionAward();
        paidAward.setPromoter(winner);
        paidAward.setStatus("PAID");
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of(paidAward));

        assertThatThrownBy(() -> sut().resolveTie(tie.getUuid(), List.of(winner.getUuid()),
                "motivo suficientemente largo", UUID.randomUUID(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("competitive_award.already_paid");
    }

    @Test
    void decide_redirect_pinsReplacementAndExcludesCurrentHolder() {
        CompetitiveCommissionRule rule = rule();
        Promoter currentHolder = promoter(1L, UUID.randomUUID());
        Promoter replacement = promoter(2L, UUID.randomUUID());
        when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));
        when(promoterRepository.findByUuid(currentHolder.getUuid())).thenReturn(Optional.of(currentHolder));
        when(promoterRepository.findByUuid(replacement.getUuid())).thenReturn(Optional.of(replacement));
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());

        sut().decide(rule.getUuid(), LocalDate.of(2026, 9, 1), Kind.REDIRECT, 2, currentHolder.getUuid(),
                replacement.getUuid(), false, ReasonCategory.POLICY, "conducta antideportiva confirmada",
                UUID.randomUUID(), false);

        ArgumentCaptor<CompetitiveCommissionManualDecision> captor = ArgumentCaptor.forClass(CompetitiveCommissionManualDecision.class);
        verify(decisionRepository).save(captor.capture());
        assertThat(captor.getValue().getPromoter()).isEqualTo(replacement); // the pinned NEW winner
        assertThat(captor.getValue().getReplacedPromoter()).isEqualTo(currentHolder); // who lost the spot
        assertThat(captor.getValue().getAwardPosition()).isEqualTo(2);
    }

    @Test
    void decide_redirect_missingReplacement_rejected() {
        CompetitiveCommissionRule rule = rule();
        when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));
        when(promoterRepository.findByUuid(any())).thenReturn(Optional.of(promoter(1L, UUID.randomUUID())));
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> sut().decide(rule.getUuid(), LocalDate.of(2026, 9, 1), Kind.REDIRECT, null,
                UUID.randomUUID(), null, false, ReasonCategory.OTHER, "motivo suficientemente largo",
                UUID.randomUUID(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_decision.invalid_replacement");
    }

    @Test
    void revert_notActive_rejected() {
        CompetitiveCommissionManualDecision decision = new CompetitiveCommissionManualDecision();
        decision.setUuid(UUID.randomUUID());
        decision.setStatus("REVERTED");
        when(decisionRepository.findByUuid(decision.getUuid())).thenReturn(Optional.of(decision));

        assertThatThrownBy(() -> sut().revert(decision.getUuid(), "motivo suficientemente largo", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("competitive_decision.not_active");
    }
}
