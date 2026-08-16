package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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
}
