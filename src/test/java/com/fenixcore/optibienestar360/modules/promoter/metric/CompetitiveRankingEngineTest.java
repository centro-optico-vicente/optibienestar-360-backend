package com.fenixcore.optibienestar360.modules.promoter.metric;

import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.OpenTie;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.PositionSpec;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.ProjectedAward;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.RankingResult;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.TiePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitiveRankingEngineTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    private static Candidate candidate(long promoterId, long value, int minutesAfterT0, int txCount) {
        return new Candidate(promoterId, BigDecimal.valueOf(value), T0.plus(minutesAfterT0, ChronoUnit.MINUTES), txCount);
    }

    private static PositionSpec spec(int from, int to) {
        return new PositionSpec(from, to, null, null);
    }

    private static Map<Integer, Long> positionOf(RankingResult result) {
        return result.awards().stream().collect(Collectors.toMap(ProjectedAward::position, ProjectedAward::promoterId));
    }

    // ─── FIRST_TO_REACH ─────────────────────────────────────────────────────

    @Test
    void firstToReach_countThreshold_stopsAtTheEventThatCrossesIt() {
        List<MetricEvent> events = List.of(
                new MetricEvent(1L, BigDecimal.ONE, T0),
                new MetricEvent(1L, BigDecimal.ONE, T0.plusSeconds(60)),
                new MetricEvent(1L, BigDecimal.ONE, T0.plusSeconds(120)), // crosses 3 here
                new MetricEvent(1L, BigDecimal.ONE, T0.plusSeconds(180))); // never counted

        List<Candidate> reached = CompetitiveRankingEngine.firstToReach(events, BigDecimal.valueOf(3));

        assertThat(reached).hasSize(1);
        Candidate winner = reached.get(0);
        assertThat(winner.value()).isEqualByComparingTo("3");
        assertThat(winner.achievedAt()).isEqualTo(T0.plusSeconds(120));
        assertThat(winner.transactionCount()).isEqualTo(3);
    }

    @Test
    void firstToReach_amountThreshold_ordersByWhoCrossedFirst() {
        List<MetricEvent> events = List.of(
                new MetricEvent(1L, BigDecimal.valueOf(60), T0.plusSeconds(10)),
                new MetricEvent(1L, BigDecimal.valueOf(50), T0.plusSeconds(20)), // promoter 1 crosses 100 here
                new MetricEvent(2L, BigDecimal.valueOf(100), T0.plusSeconds(5))); // promoter 2 crosses 100 immediately, earlier

        List<Candidate> reached = CompetitiveRankingEngine.firstToReach(events, BigDecimal.valueOf(100));
        RankingResult result = CompetitiveRankingEngine.rank(reached, List.of(spec(1, 1)), false, false,
                TiePolicy.MANUAL, Map.of(), Set.of());

        assertThat(positionOf(result)).containsEntry(1, 2L);
    }

    @Test
    void firstToReach_promotersWhoNeverCross_areDropped() {
        List<MetricEvent> events = List.of(new MetricEvent(1L, BigDecimal.valueOf(2), T0));
        assertThat(CompetitiveRankingEngine.firstToReach(events, BigDecimal.valueOf(3))).isEmpty();
    }

    // ─── Position ranges ────────────────────────────────────────────────────

    @Test
    void rank_singleWinner_range1to1() {
        List<Candidate> candidates = List.of(candidate(1, 100, 0, 1), candidate(2, 50, 0, 1));
        RankingResult result = rankAuto(candidates, List.of(spec(1, 1)));
        assertThat(positionOf(result)).containsExactly(Map.entry(1, 1L));
    }

    @Test
    void rank_flatRange_1to5_paysEveryoneTheSameTier() {
        List<Candidate> candidates = List.of(
                candidate(1, 100, 0, 1), candidate(2, 90, 0, 1), candidate(3, 80, 0, 1),
                candidate(4, 70, 0, 1), candidate(5, 60, 0, 1), candidate(6, 50, 0, 1));
        RankingResult result = rankAuto(candidates, List.of(spec(1, 5)));
        assertThat(positionOf(result).keySet()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertThat(positionOf(result)).doesNotContainValue(6L);
    }

    @Test
    void rank_tieredRanges_1to1_and_2to4_withGapAt5to9() {
        List<Candidate> candidates = List.of(
                candidate(1, 100, 0, 1), candidate(2, 90, 0, 1), candidate(3, 80, 0, 1),
                candidate(4, 70, 0, 1), candidate(5, 60, 0, 1));
        RankingResult result = rankAuto(candidates, List.of(spec(1, 1), spec(2, 4)));
        Map<Integer, Long> byPosition = positionOf(result);
        assertThat(byPosition).containsEntry(1, 1L).containsEntry(2, 2L).containsEntry(3, 3L).containsEntry(4, 4L);
        assertThat(byPosition).doesNotContainKey(5); // 5th place isn't covered by any range — no award
    }

    @Test
    void rank_fewerQualifiersThanPositions_leavesTheRestVacant() {
        List<Candidate> candidates = List.of(candidate(1, 100, 0, 1), candidate(2, 90, 0, 1));
        RankingResult result = rankAuto(candidates, List.of(spec(1, 1), spec(2, 4)));
        assertThat(positionOf(result)).containsOnly(Map.entry(1, 1L), Map.entry(2, 2L));
    }

    // ─── D12: per-position minimums (RANKING only) ─────────────────────────

    @Test
    void rank_occupantBelowPositionMinimum_leavesItVacant_withoutCascading() {
        PositionSpec first = new PositionSpec(1, 1, 100, null); // requires >= 100 (count metric)
        List<Candidate> candidates = List.of(candidate(1, 80, 0, 1), candidate(2, 70, 0, 1));

        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(first), true, true,
                TiePolicy.MANUAL, Map.of(), Set.of());

        assertThat(result.awards()).isEmpty(); // promoter 1 fails the min and is NOT promoted to a lower tier
    }

    @Test
    void rank_minimumOnlyBlocksItsOwnTier() {
        PositionSpec first = new PositionSpec(1, 1, 100, null);
        PositionSpec second = new PositionSpec(2, 2, null, null); // no minimum
        List<Candidate> candidates = List.of(candidate(1, 80, 0, 1), candidate(2, 70, 0, 1));

        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(first, second), true, true,
                TiePolicy.MANUAL, Map.of(), Set.of());

        // Promoter 1 (who'd occupy position 1) fails the min and gets nothing — position 2 stays
        // with whoever is actually next (promoter 2), per the literal "no cascade" reading of D12.
        assertThat(positionOf(result)).containsOnly(Map.entry(2, 2L));
    }

    // ─── D6 automatic tiebreak + D16 manual escalation ─────────────────────

    @Test
    void rank_tieThatFitsInsideOneRange_needsNoIntervention() {
        // 2 candidates dead-tied on value/achievedAt/txCount, both landing inside the same 2..4
        // reward tier — the reward is identical either way, so no OpenTie is needed.
        List<Candidate> candidates = List.of(
                candidate(1, 100, 0, 1),
                candidate(2, 50, 5, 2), candidate(3, 50, 5, 2),
                candidate(4, 40, 0, 1));
        RankingResult result = rankAuto(candidates, List.of(spec(1, 1), spec(2, 4)));

        assertThat(result.openTie()).isNull();
        assertThat(positionOf(result).keySet()).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    @Test
    void rank_manualPolicy_tieThatCrossesABoundary_opensATie() {
        // The user's exact scenario: 1..1 pays A, 2..4 pays B. 3 candidates dead-tied for the top
        // value (1 slot only) — the automatic criteria can't break it further, so it must open.
        List<Candidate> candidates = List.of(
                candidate(1, 100, 0, 1), candidate(2, 100, 0, 1), candidate(3, 100, 0, 1),
                candidate(4, 50, 0, 1), candidate(5, 50, 0, 1), candidate(6, 50, 0, 1));

        RankingResult result = rankAuto(candidates, List.of(spec(1, 1), spec(2, 4)));

        assertThat(result.awards()).isEmpty(); // nothing at or below the tie is assigned this run
        OpenTie tie = result.openTie();
        assertThat(tie).isNotNull();
        assertThat(tie.positionFrom()).isEqualTo(1);
        assertThat(tie.slots()).isEqualTo(1);
        assertThat(tie.candidates()).extracting(Candidate::promoterId).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void rank_manualPolicy_afterPinningOneWinner_theSpilloverAutoFillsAndReopensTheNextTie() {
        // Continuing the example above: the coordinator picked promoter 1 for position 1.
        // Promoters 2 and 3 (tied at 100, clearly outranking the 50s) automatically fill the first
        // 2 of B's 3 slots — no ambiguity there. That leaves exactly 1 B slot for 4/5/6's own tie.
        List<Candidate> candidates = List.of(
                candidate(1, 100, 0, 1), candidate(2, 100, 0, 1), candidate(3, 100, 0, 1),
                candidate(4, 50, 0, 1), candidate(5, 50, 0, 1), candidate(6, 50, 0, 1));

        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(spec(1, 1), spec(2, 4)), false, false,
                TiePolicy.MANUAL, Map.of(1, 1L), Set.of());

        assertThat(result.awards()).filteredOn(a -> a.promoterId() == 1L).extracting(ProjectedAward::position)
                .containsExactly(1);
        assertThat(result.awards()).filteredOn(a -> a.promoterId() == 2L || a.promoterId() == 3L)
                .extracting(ProjectedAward::position).containsExactlyInAnyOrder(2, 3);
        OpenTie tie = result.openTie();
        assertThat(tie).isNotNull();
        assertThat(tie.positionFrom()).isEqualTo(4);
        assertThat(tie.slots()).isEqualTo(1); // the single remaining B slot
        assertThat(tie.candidates()).extracting(Candidate::promoterId).containsExactlyInAnyOrder(4L, 5L, 6L);
    }

    @Test
    void rank_manualPolicy_allCandidatesTiedTogether_opensASingleTieForTheTopSlot() {
        // Variant: all 6 candidates share one identical value — a single tie of 6 for tier 1's 1 slot.
        List<Candidate> candidates = List.of(
                candidate(1, 100, 0, 1), candidate(2, 100, 0, 1), candidate(3, 100, 0, 1),
                candidate(4, 100, 0, 1), candidate(5, 100, 0, 1), candidate(6, 100, 0, 1));

        RankingResult result = rankAuto(candidates, List.of(spec(1, 1), spec(2, 4)));

        OpenTie tie = result.openTie();
        assertThat(tie).isNotNull();
        assertThat(tie.positionFrom()).isEqualTo(1);
        assertThat(tie.slots()).isEqualTo(1);
        assertThat(tie.candidates()).hasSize(6);
    }

    @Test
    void rank_strictPolicy_neverOpensATie_truncatesByDeterministicOrder() {
        List<Candidate> candidates = List.of(
                candidate(3, 100, 0, 1), candidate(1, 100, 0, 1), candidate(2, 100, 0, 1)); // tied; sorted by id asc

        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(spec(1, 1)), false, false,
                TiePolicy.STRICT, Map.of(), Set.of());

        assertThat(result.openTie()).isNull();
        assertThat(positionOf(result)).containsExactly(Map.entry(1, 1L)); // lowest promoter id wins, arbitrarily but stably
    }

    @Test
    void rank_strictPolicy_leftoverTiedMembersCascadeToTheNextTier() {
        List<Candidate> candidates = List.of(candidate(1, 100, 0, 1), candidate(2, 100, 0, 1), candidate(3, 100, 0, 1));

        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(spec(1, 1), spec(2, 2)), false, false,
                TiePolicy.STRICT, Map.of(), Set.of());

        assertThat(positionOf(result)).containsOnly(Map.entry(1, 1L), Map.entry(2, 2L));
    }

    @Test
    void rank_sharedFull_everyTiedMemberGetsTheRicherTier_consumingItEntirely() {
        List<Candidate> candidates = List.of(candidate(1, 100, 0, 1), candidate(2, 100, 0, 1), candidate(3, 100, 0, 1));

        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(spec(1, 1)), false, false,
                TiePolicy.SHARED_FULL, Map.of(), Set.of());

        assertThat(result.awards()).extracting(ProjectedAward::position).containsOnly(1);
        assertThat(result.awards()).hasSize(3);
        assertThat(result.awards().get(0).tieGroupSize()).isEqualTo(3);
    }

    @Test
    void rank_sharedFull_leftoverBelowTheTieStillFillsTheNextTier() {
        List<Candidate> candidates = List.of(
                candidate(1, 100, 0, 1), candidate(2, 100, 0, 1), candidate(3, 100, 0, 1),
                candidate(4, 50, 0, 1));

        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(spec(1, 1), spec(2, 4)), false, false,
                TiePolicy.SHARED_FULL, Map.of(), Set.of());

        // Tier 1's single slot is consumed by all 3 tied members (each at position 1); promoter 4
        // is untouched by the tie and still gets the next tier's first free position.
        assertThat(result.awards()).hasSize(4);
        assertThat(result.awards()).filteredOn(a -> a.promoterId() == 4L)
                .extracting(ProjectedAward::position).containsExactly(2);
    }

    // ─── D16: pins and exclusions ───────────────────────────────────────────

    @Test
    void rank_exclusion_removesAPromoterFromContentionEntirely() {
        List<Candidate> candidates = List.of(candidate(1, 100, 0, 1), candidate(2, 90, 0, 1));
        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(spec(1, 1)), false, false,
                TiePolicy.MANUAL, Map.of(), Set.of(1L));
        assertThat(positionOf(result)).containsExactly(Map.entry(1, 2L));
    }

    @Test
    void rank_pin_isHonoredEvenIfNotTheTopValue() {
        List<Candidate> candidates = List.of(candidate(1, 100, 0, 1), candidate(2, 90, 0, 1), candidate(3, 80, 0, 1));
        RankingResult result = CompetitiveRankingEngine.rank(candidates, List.of(spec(1, 2)), false, false,
                TiePolicy.MANUAL, Map.of(1, 3L), Set.of());

        Map<Integer, Long> byPosition = positionOf(result);
        assertThat(byPosition).containsEntry(1, 3L);
        assertThat(byPosition).containsEntry(2, 1L); // the pinned promoter is pulled from the pool; the
        // next-best of the remaining 2 candidates (1, then 2) fills the other slot.
    }

    private static RankingResult rankAuto(List<Candidate> candidates, List<PositionSpec> positions) {
        return CompetitiveRankingEngine.rank(candidates, positions, false, false, TiePolicy.MANUAL, Map.of(), Set.of());
    }
}
