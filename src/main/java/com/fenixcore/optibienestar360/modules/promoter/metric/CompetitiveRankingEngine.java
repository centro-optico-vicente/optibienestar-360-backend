package com.fenixcore.optibienestar360.modules.promoter.metric;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure (no Spring) ranking core for competitive commission rules. Takes a pool of candidates
 * already reduced to one row per promoter (see {@link #firstToReach}) plus the rule's position
 * ranges, and assigns award positions.
 *
 * <p>Reward money is <b>not</b> computed here — the caller resolves {@code amount} from the
 * {@code PositionSpec} matching {@link ProjectedAward#position()}. This engine only decides
 * <i>who</i> occupies <i>which</i> position, and where a human must decide (D16).
 *
 * <p><b>Slot model:</b> a position range {@code from..to} holds {@code to - from + 1} physical
 * slots, all paying the same reward. Candidates are sorted once (metric value desc, achieved-at
 * asc, transaction count desc, promoter id asc — the last a purely deterministic, meaningless
 * tiebreak) and consumed slot range by slot range. A "residual tie" is a run of candidates equal
 * on the first three criteria — i.e. truly indistinguishable without a human call.
 *
 * <ul>
 *   <li>A residual tie that fits entirely within the remaining slots of the range it lands in is
 *       <b>not</b> ambiguous (every member gets the same reward either way) and is assigned
 *       without intervention.</li>
 *   <li>A residual tie that spills past the remaining slots of its range crosses a reward
 *       boundary: some members would get a richer prize than others depending on an arbitrary
 *       pick. This engine stops there and returns an {@link OpenTie} instead of guessing —
 *       nothing below that point is assigned, since who spills down depends on the human's
 *       choice (D16 cascade).</li>
 * </ul>
 */
public final class CompetitiveRankingEngine {

    private CompetitiveRankingEngine() {
    }

    /** One physical, moneyed prize range on the rule. D12: a min may apply only to RANKING rules. */
    public record PositionSpec(int positionFrom, int positionTo, Integer minThresholdCount, BigDecimal minThresholdAmount) {
        public PositionSpec {
            if (positionTo < positionFrom) {
                throw new IllegalArgumentException("competitive_ranking_engine.invalid_position_range");
            }
        }

        public int slots() {
            return positionTo - positionFrom + 1;
        }

        boolean meetsMinimum(BigDecimal value, boolean countMetric) {
            if (countMetric) {
                return minThresholdCount == null || value.compareTo(BigDecimal.valueOf(minThresholdCount)) >= 0;
            }
            return minThresholdAmount == null || value.compareTo(minThresholdAmount) >= 0;
        }
    }

    /** One promoter's resolved standing for the period: total (or FIRST_TO_REACH crossing) value. */
    public record Candidate(Long promoterId, BigDecimal value, Instant achievedAt, int transactionCount) {
    }

    public record ProjectedAward(int position, Long promoterId, BigDecimal metricValue, Instant achievedAt,
                                  int transactionCount, int tieGroupSize, SelectionSource selectionSource) {
    }

    public enum SelectionSource {
        AUTO, MANUAL
    }

    /** A tie the engine could not resolve on its own: {@code candidates.size() > slots}. */
    public record OpenTie(int positionFrom, int slots, List<Candidate> candidates) {
    }

    public record RankingResult(List<ProjectedAward> awards, OpenTie openTie) {
        public static RankingResult empty() {
            return new RankingResult(List.of(), null);
        }
    }

    private static final Comparator<Candidate> DETERMINISTIC_ORDER = Comparator
            .comparing(Candidate::value, Comparator.reverseOrder())
            .thenComparing(c -> c.achievedAt() == null ? Instant.MAX : c.achievedAt())
            .thenComparing(Candidate::transactionCount, Comparator.reverseOrder())
            .thenComparing(Candidate::promoterId);

    /**
     * FIRST_TO_REACH: reduces raw, per-transaction {@link MetricEvent}s into one {@link Candidate}
     * per promoter — the moment (and running total) at which their cumulative value first met
     * {@code threshold}. Promoters who never reach it are dropped; events are consumed in
     * {@code achievedAt} order per promoter, so {@code achievedAt}/{@code transactionCount} on the
     * resulting candidate are exactly "when they crossed the line" and "in how many transactions".
     */
    public static List<Candidate> firstToReach(List<MetricEvent> events, BigDecimal threshold) {
        Map<Long, List<MetricEvent>> byPromoter = new java.util.LinkedHashMap<>();
        for (MetricEvent event : events) {
            byPromoter.computeIfAbsent(event.promoterId(), k -> new ArrayList<>()).add(event);
        }
        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<Long, List<MetricEvent>> entry : byPromoter.entrySet()) {
            List<MetricEvent> ordered = new ArrayList<>(entry.getValue());
            ordered.sort(Comparator.comparing(MetricEvent::achievedAt));
            BigDecimal cumulative = BigDecimal.ZERO;
            int count = 0;
            for (MetricEvent event : ordered) {
                cumulative = cumulative.add(event.value());
                count++;
                if (cumulative.compareTo(threshold) >= 0) {
                    result.add(new Candidate(entry.getKey(), cumulative, event.achievedAt(), count));
                    break;
                }
            }
        }
        return result;
    }

    public enum TiePolicy {
        STRICT, SHARED_FULL, SHARED_SPLIT, MANUAL
    }

    /**
     * Assigns positions from a candidate pool. {@code pins} pre-assigns a position to a promoter
     * (a prior manual decision) — that promoter is pulled out of the general pool and their slot
     * is removed from the tier's remaining capacity; {@code exclusions} removes promoters from
     * the pool entirely (disqualified, or already won a higher-priority rule in the same D16
     * group). Pass {@code applyMinimums = false} for FIRST_TO_REACH rules — D12 minimums are
     * RANKING-only.
     *
     * <p>{@code tiePolicy} only matters once a residual tie group overflows the tier it lands in
     * (fitting entirely inside one tier is never ambiguous, regardless of policy):
     * <ul>
     *   <li>{@code STRICT} — takes as many as fit, in the same arbitrary-but-stable order used to
     *       sort the pool (never opens a tie; the leftover members compete again for the next
     *       tier down, exactly like today's leaderboard).</li>
     *   <li>{@code SHARED_FULL}/{@code SHARED_SPLIT} — every tied member gets the tier's own
     *       (richer) position, consuming its whole remaining capacity regardless of group size;
     *       the caller decides whether that means each gets the full reward or a split share.</li>
     *   <li>{@code MANUAL} (the default for new rules) — stops and returns an {@link OpenTie}
     *       instead of guessing; nothing at or below that point is assigned this run.</li>
     * </ul>
     */
    public static RankingResult rank(List<Candidate> candidates, List<PositionSpec> positions, boolean countMetric,
                                      boolean applyMinimums, TiePolicy tiePolicy, Map<Integer, Long> pins, Set<Long> exclusions) {
        Map<Integer, Long> effectivePins = pins == null ? Map.of() : pins;
        Set<Long> effectiveExclusions = exclusions == null ? Set.of() : exclusions;
        Set<Long> pinnedPromoters = new java.util.HashSet<>(effectivePins.values());

        List<Candidate> pool = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (effectiveExclusions.contains(candidate.promoterId()) || pinnedPromoters.contains(candidate.promoterId())) {
                continue;
            }
            pool.add(candidate);
        }
        pool.sort(DETERMINISTIC_ORDER);

        Map<Long, Candidate> byId = new java.util.HashMap<>();
        for (Candidate candidate : candidates) {
            byId.put(candidate.promoterId(), candidate);
        }

        List<ProjectedAward> awards = new ArrayList<>();
        int poolIndex = 0;
        for (PositionSpec tier : sortedByFrom(positions)) {
            java.util.Deque<Integer> freePositions = new java.util.ArrayDeque<>();
            for (int position = tier.positionFrom(); position <= tier.positionTo(); position++) {
                Long pinned = effectivePins.get(position);
                if (pinned != null) {
                    Candidate pinnedCandidate = byId.get(pinned);
                    BigDecimal value = pinnedCandidate != null ? pinnedCandidate.value() : BigDecimal.ZERO;
                    Instant achievedAt = pinnedCandidate != null ? pinnedCandidate.achievedAt() : null;
                    int txCount = pinnedCandidate != null ? pinnedCandidate.transactionCount() : 0;
                    awards.add(new ProjectedAward(position, pinned, value, achievedAt, txCount, 1, SelectionSource.MANUAL));
                } else {
                    freePositions.addLast(position);
                }
            }

            while (poolIndex < pool.size() && !freePositions.isEmpty()) {
                Candidate head = pool.get(poolIndex);
                int groupEnd = poolIndex + 1;
                while (groupEnd < pool.size() && isResidualTie(head, pool.get(groupEnd))) {
                    groupEnd++;
                }
                int groupSize = groupEnd - poolIndex;

                if (groupSize > freePositions.size()) {
                    switch (tiePolicy) {
                        case MANUAL -> {
                            List<Candidate> tieCandidates = pool.subList(poolIndex, groupEnd);
                            return new RankingResult(awards, new OpenTie(freePositions.peekFirst(), freePositions.size(),
                                    List.copyOf(tieCandidates)));
                        }
                        case STRICT -> {
                            // Take only as many as fit, in the pool's own deterministic order; the
                            // rest stay in the pool and compete again for the next tier down.
                            int take = freePositions.size();
                            for (int i = 0; i < take; i++) {
                                Candidate c = pool.get(poolIndex + i);
                                awards.add(new ProjectedAward(freePositions.pollFirst(), c.promoterId(), c.value(),
                                        c.achievedAt(), c.transactionCount(), groupSize, SelectionSource.AUTO));
                            }
                            poolIndex += take;
                        }
                        case SHARED_FULL, SHARED_SPLIT -> {
                            int sharedPosition = freePositions.peekFirst();
                            for (int i = poolIndex; i < groupEnd; i++) {
                                Candidate c = pool.get(i);
                                awards.add(new ProjectedAward(sharedPosition, c.promoterId(), c.value(), c.achievedAt(),
                                        c.transactionCount(), groupSize, SelectionSource.AUTO));
                            }
                            freePositions.clear();
                            poolIndex = groupEnd;
                        }
                    }
                    continue;
                }

                for (int i = poolIndex; i < groupEnd; i++) {
                    Candidate c = pool.get(i);
                    awards.add(new ProjectedAward(freePositions.pollFirst(), c.promoterId(), c.value(), c.achievedAt(),
                            c.transactionCount(), groupSize, SelectionSource.AUTO));
                }
                poolIndex = groupEnd;
            }
        }
        return new RankingResult(applyMinimums ? withMinimumsApplied(awards, positions, countMetric) : awards, null);
    }

    /**
     * D12 post-filter: ranks are assigned purely by order, with no cascade of any kind — a
     * candidate's position never moves because of a minimum. This only decides whether an
     * already-assigned (AUTO) slot is actually paid: an occupant below their own position's
     * minimum simply loses the award, leaving that position vacant; every other position's
     * occupant (in the same tier or any other) is completely unaffected. Pinned (MANUAL) awards
     * are a human's decision and are never subject to D12.
     */
    private static List<ProjectedAward> withMinimumsApplied(List<ProjectedAward> awards, List<PositionSpec> positions,
                                                              boolean countMetric) {
        List<ProjectedAward> filtered = new ArrayList<>();
        for (ProjectedAward award : awards) {
            if (award.selectionSource() == SelectionSource.MANUAL) {
                filtered.add(award);
                continue;
            }
            PositionSpec tier = tierContaining(positions, award.position());
            if (tier == null || tier.meetsMinimum(award.metricValue(), countMetric)) {
                filtered.add(award);
            }
        }
        return filtered;
    }

    private static PositionSpec tierContaining(List<PositionSpec> positions, int position) {
        for (PositionSpec tier : positions) {
            if (position >= tier.positionFrom() && position <= tier.positionTo()) {
                return tier;
            }
        }
        return null;
    }

    private static boolean isResidualTie(Candidate a, Candidate b) {
        boolean sameValue = a.value().compareTo(b.value()) == 0;
        boolean sameAchievedAt = java.util.Objects.equals(a.achievedAt(), b.achievedAt());
        return sameValue && sameAchievedAt && a.transactionCount() == b.transactionCount();
    }

    private static List<PositionSpec> sortedByFrom(List<PositionSpec> positions) {
        List<PositionSpec> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingInt(PositionSpec::positionFrom));
        return sorted;
    }
}
