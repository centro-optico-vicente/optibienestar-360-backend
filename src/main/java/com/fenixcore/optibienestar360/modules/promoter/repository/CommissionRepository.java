package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CommissionRepository extends JpaRepository<Commission, Long>,
        JpaSpecificationExecutor<Commission> {

    Optional<Commission> findByUuid(UUID uuid);

    /**
     * Used by the commission engine before INSERT — V26 partial UNIQUE
     * {@code (payment_id, promoter_id) WHERE status <> 'VOIDED'} allows
     * a fresh commission after a void, so existence check filters by
     * non-voided. The Specification path is overkill for this one call.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT (COUNT(c) > 0) FROM Commission c
            WHERE c.payment.id = :paymentId
              AND c.promoter.id = :promoterId
              AND c.status <> 'VOIDED'
            """)
    boolean existsActiveForPaymentAndPromoter(
            @org.springframework.data.repository.query.Param("paymentId") Long paymentId,
            @org.springframework.data.repository.query.Param("promoterId") Long promoterId);

    /** All commissions of a payment (for the void fan-out path). */
    List<Commission> findByPaymentId(Long paymentId);

    /** Usage check for {@code MembersService.countUsages} — ALL rows (active + inactive). */
    long countByMemberId(Long memberId);

    /** Usage check for {@code PromotersService.countUsages} — ALL rows (active + inactive). */
    long countByPromoterId(Long promoterId);

    /**
     * Usage check for {@code CommissionTiersService.countUsages} — the FK was
     * closed by V42 ({@code fk_commissions_tier}) even though the entity still
     * maps {@code commissionTierId} as a bare {@code Long} (see the Javadoc on
     * {@link Commission}, which predates that migration and is stale on this
     * point). Counts ALL rows (active + inactive/voided) since a real FK row
     * of any status would still block a hard delete at the DB level.
     */
    long countByCommissionTierId(Long commissionTierId);

    /**
     * Usage check for {@code CollectionCommissionTiersService.countUsages} —
     * FK {@code commissions.collection_tier_id → collection_commission_tiers}
     * added directly by V48 (never deferred). Same bare-{@code Long} mapping
     * caveat as {@link #countByCommissionTierId}.
     */
    long countByCollectionTierId(Long collectionTierId);

    /**
     * Every PENDING commission whose period falls inside the requested
     * range — powers {@code CommissionReRatingService} (month-close
     * re-rating, PR3), which deliberately still targets PENDING: re-rating
     * bumps a row's amount to the period's final band <i>before</i> gerencia
     * comercial reviews it, so what gets approved is already the correct
     * final figure (re-rating an already-{@code APPROVED} row would
     * silently change a number someone already signed off on — out of
     * scope; that needs a re-approval flow, not a silent rewrite). Joins the
     * partial composite index {@code idx_commissions_promoter_status_period}
     * via the predicate {@code status='PENDING'} plus the period bounds.
     *
     * <p><b>Not used by the payout</b> (V107, PR6) — {@code
     * CommissionPayoutService} requires {@link #findApprovedForPeriod}
     * instead, since administración must only ever disburse what comercial
     * already approved.</p>
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Commission c
            WHERE c.active = true
              AND c.status = 'PENDING'
              AND c.periodStart >= :periodStart
              AND c.periodEnd   <= :periodEnd
            ORDER BY c.promoter.id, c.earnedAt
            """)
    List<Commission> findPendingForPeriod(
            @org.springframework.data.repository.query.Param("periodStart") java.time.LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd")   java.time.LocalDate periodEnd);

    /**
     * Powers the period payout (V107, PR6) — every APPROVED commission
     * whose period falls inside the requested range. Administración can
     * only ever disburse what gerencia comercial already reviewed and
     * approved; a still-{@code PENDING} (unreviewed) or {@code REJECTED}
     * row is never picked up here, regardless of how long it's been
     * sitting. Same index shape as {@link #findPendingForPeriod}.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Commission c
            WHERE c.active = true
              AND c.status = 'APPROVED'
              AND c.periodStart >= :periodStart
              AND c.periodEnd   <= :periodEnd
            ORDER BY c.promoter.id, c.earnedAt
            """)
    List<Commission> findApprovedForPeriod(
            @org.springframework.data.repository.query.Param("periodStart") java.time.LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd")   java.time.LocalDate periodEnd);

    /**
     * Commissions a promoter earned within a period, for the self-service
     * dashboard ({@code GET /v1/promoter/me}). Sums non-voided rows whose
     * period falls inside the range; {@code COALESCE} keeps it 0 (never null)
     * when the promoter has none.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COALESCE(SUM(c.amount), 0) FROM Commission c
            WHERE c.active = true
              AND c.promoter.id = :promoterId
              AND c.status <> 'VOIDED'
              AND c.periodStart >= :periodStart
              AND c.periodEnd   <= :periodEnd
            """)
    BigDecimal sumForPromoterInPeriod(
            @org.springframework.data.repository.query.Param("promoterId") Long promoterId,
            @org.springframework.data.repository.query.Param("periodStart") LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd")   LocalDate periodEnd);

    /**
     * Hierarchy-override engine (V102): count of commissions of a given {@link
     * Commission.AppliesTo} earned across an entire team subtree within a
     * period — the caller passes {@code MONTHLY} for the team-volume metric
     * COLLECTION {@code hierarchy_override_tiers} qualify against. Documented
     * as a TBD to confirm with the business (hub plan §2) — this is the
     * initial proposal ("conteo de comisiones MONTHLY del subárbol").
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(c) FROM Commission c
            WHERE c.active = true
              AND c.promoter.id IN :promoterIds
              AND c.appliesTo = :appliesTo
              AND c.periodStart >= :periodStart
              AND c.periodEnd   <= :periodEnd
            """)
    long countByPromotersAppliesToInPeriod(
            @org.springframework.data.repository.query.Param("promoterIds") Collection<Long> promoterIds,
            @org.springframework.data.repository.query.Param("appliesTo") Commission.AppliesTo appliesTo,
            @org.springframework.data.repository.query.Param("periodStart") LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd")   LocalDate periodEnd);

    /**
     * Powers {@code CommissionRetroactiveTopUpService} (V105, PR4) — every
     * PAID commission whose period falls inside the settlement range. These
     * are the rows a partial-cut payout already locked in at whatever band
     * was qualifying at the time; the top-up service computes the gap
     * between what they paid and the final highest band, never mutating
     * them directly (mirrors {@link #findPendingForPeriod}'s shape).
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Commission c
            WHERE c.active = true
              AND c.status = 'PAID'
              AND c.periodStart >= :periodStart
              AND c.periodEnd   <= :periodEnd
            ORDER BY c.promoter.id, c.earnedAt
            """)
    List<Commission> findPaidForPeriod(
            @org.springframework.data.repository.query.Param("periodStart") LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd")   LocalDate periodEnd);

    /**
     * Powers {@code CommissionPeriodicSettlementService} (V112, hub plan §3)
     * — every APPROVED commission of a single promoter, scoped to one
     * {@link Commission.AppliesTo}, whose period falls inside a single
     * <b>cut</b> (not the whole settlement window — the caller passes the
     * {@code PeriodCutCalculator} cut bounds). Mirrors {@link
     * #findApprovedForPeriod} but promoter-scoped, since a partial-cut
     * settlement re-prices one promoter's cut at a time.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Commission c
            WHERE c.active = true
              AND c.status = 'APPROVED'
              AND c.promoter.id = :promoterId
              AND c.appliesTo = :appliesTo
              AND c.periodStart >= :cutStart
              AND c.periodEnd   <= :cutEnd
            ORDER BY c.earnedAt
            """)
    List<Commission> findApprovedForPromoterAppliesToInPeriod(
            @org.springframework.data.repository.query.Param("promoterId") Long promoterId,
            @org.springframework.data.repository.query.Param("appliesTo") Commission.AppliesTo appliesTo,
            @org.springframework.data.repository.query.Param("cutStart") LocalDate cutStart,
            @org.springframework.data.repository.query.Param("cutEnd")   LocalDate cutEnd);

    /**
     * Powers {@code CommissionApprovalService.approveRows}/{@code
     * rejectRows} (V107, PR5) — resolves the exact rows the request targets
     * regardless of status, so the service itself decides whether each one
     * is eligible (only {@code PENDING} is) instead of the query silently
     * filtering some out.
     */
    List<Commission> findByUuidIn(Collection<UUID> uuids);

    /**
     * Powers {@code GET /v1/admin/commissions/approval-queue} (V107, PR5) —
     * every commission in the period regardless of status, so the 2-level
     * approval table can show the period's full "todo" (including
     * already-APPROVED/PAID rows, read-only) alongside the still-PENDING
     * ones awaiting a decision.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Commission c
            WHERE c.active = true
              AND c.periodStart >= :periodStart
              AND c.periodEnd   <= :periodEnd
            ORDER BY c.promoter.id, c.earnedAt
            """)
    List<Commission> findAllForPeriod(
            @org.springframework.data.repository.query.Param("periodStart") LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd")   LocalDate periodEnd);
}
