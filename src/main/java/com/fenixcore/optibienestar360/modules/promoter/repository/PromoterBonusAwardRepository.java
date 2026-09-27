package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward.CutKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PromoterBonusAwardRepository extends JpaRepository<PromoterBonusAward, Long>,
        JpaSpecificationExecutor<PromoterBonusAward> {

    Optional<PromoterBonusAward> findByUuid(UUID uuid);

    /** Usage check for {@code PromotersService.countUsages} — ALL rows (active + inactive). */
    long countByPromoterId(Long promoterId);

    /** A promoter's own awards, newest first (self-service {@code /v1/promoter/me/bonuses}). */
    Page<PromoterBonusAward> findByPromoterIdAndActiveTrueOrderByCreatedAtDesc(Long promoterId, Pageable pageable);

    /**
     * Blocks already granted to a promoter for a LIFETIME rule across every
     * window (dedup for cumulative per-block milestones). {@code COALESCE} keeps
     * it 0 when there are none; VOIDED rows don't count.
     */
    @Query("""
            SELECT COALESCE(SUM(a.blocksAwarded), 0) FROM PromoterBonusAward a
            WHERE a.rule.id = :ruleId
              AND a.promoter.id = :promoterId
              AND a.active = true
              AND a.status <> 'VOIDED'
            """)
    long sumBlocksAwardedLifetime(@Param("ruleId") Long ruleId, @Param("promoterId") Long promoterId);

    /** As {@link #sumBlocksAwardedLifetime} but scoped to a single window. */
    @Query("""
            SELECT COALESCE(SUM(a.blocksAwarded), 0) FROM PromoterBonusAward a
            WHERE a.rule.id = :ruleId
              AND a.promoter.id = :promoterId
              AND a.windowStart = :windowStart
              AND a.windowEnd   = :windowEnd
              AND a.active = true
              AND a.status <> 'VOIDED'
            """)
    long sumBlocksAwardedInWindow(@Param("ruleId") Long ruleId,
                                  @Param("promoterId") Long promoterId,
                                  @Param("windowStart") LocalDate windowStart,
                                  @Param("windowEnd") LocalDate windowEnd);

    /** THRESHOLD dedup for LIFETIME rules — has this promoter ever been awarded? */
    @Query("""
            SELECT (COUNT(a) > 0) FROM PromoterBonusAward a
            WHERE a.rule.id = :ruleId
              AND a.promoter.id = :promoterId
              AND a.active = true
              AND a.status <> 'VOIDED'
            """)
    boolean existsActiveByRuleAndPromoter(@Param("ruleId") Long ruleId, @Param("promoterId") Long promoterId);

    /** THRESHOLD dedup for windowed rules — already awarded in this window? */
    @Query("""
            SELECT (COUNT(a) > 0) FROM PromoterBonusAward a
            WHERE a.rule.id = :ruleId
              AND a.promoter.id = :promoterId
              AND a.windowStart = :windowStart
              AND a.windowEnd   = :windowEnd
              AND a.active = true
              AND a.status <> 'VOIDED'
            """)
    boolean existsActiveInWindow(@Param("ruleId") Long ruleId,
                                 @Param("promoterId") Long promoterId,
                                 @Param("windowStart") LocalDate windowStart,
                                 @Param("windowEnd") LocalDate windowEnd);

    // ─── Settlement cuts (V168, Fase B) ─────────────────────────────────────

    /** Upsert lookup by the natural key {@code uq_promoter_bonus_awards_cut}. */
    Optional<PromoterBonusAward> findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
            Long ruleId, Long promoterId, LocalDate windowStart, CutKind cutKind, short cutSequence);

    /**
     * Highest cut sequence granted so far for this axis — the {@code LIFETIME}
     * path (whose window never closes) reuses it while still {@code PENDING}
     * and increments past it only once it's {@code PAID}, so a paid-out
     * cumulative total can keep growing under a fresh row instead of being
     * silently skipped as "already paid" (see {@link
     * com.fenixcore.optibienestar360.modules.promoter.service.BonusSettlementCutService}).
     */
    Optional<PromoterBonusAward> findTopByRule_IdAndPromoter_IdAndWindowStartAndCutKindOrderByCutSequenceDesc(
            Long ruleId, Long promoterId, LocalDate windowStart, CutKind cutKind);

    /**
     * Cumulative-netting base for a due cut: everything already granted for
     * this {@code (rule, promoter, windowStart)} across every cut axis, minus
     * the one cut being recomputed right now (so re-running the same cut
     * doesn't compound against its own prior amount) and minus VOIDED rows.
     */
    @Query("""
            SELECT COALESCE(SUM(a.amount), 0) FROM PromoterBonusAward a
            WHERE a.rule.id = :ruleId
              AND a.promoter.id = :promoterId
              AND a.windowStart = :windowStart
              AND a.active = true
              AND a.status <> 'VOIDED'
              AND NOT (a.cutKind = :excludeCutKind AND a.cutSequence = :excludeCutSequence)
            """)
    BigDecimal sumGrantedExcludingCut(@Param("ruleId") Long ruleId,
                                       @Param("promoterId") Long promoterId,
                                       @Param("windowStart") LocalDate windowStart,
                                       @Param("excludeCutKind") CutKind excludeCutKind,
                                       @Param("excludeCutSequence") short excludeCutSequence);
}
