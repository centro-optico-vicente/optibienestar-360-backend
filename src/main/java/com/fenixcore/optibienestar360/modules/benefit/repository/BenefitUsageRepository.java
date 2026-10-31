package com.fenixcore.optibienestar360.modules.benefit.repository;

import com.fenixcore.optibienestar360.modules.benefit.dto.ConsumptionTotalsDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.UsagePersonSummaryDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.UsageServiceSummaryDto;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BenefitUsageRepository extends JpaRepository<BenefitUsage, Long>,
        JpaSpecificationExecutor<BenefitUsage> {

    Optional<BenefitUsage> findByUuid(UUID uuid);

    /** Usage check for {@code AlliesService.countUsages} — ALL rows (active + inactive). */
    long countByAllyId(Long allyId);

    /**
     * Powers {@code GET /v1/me/allies/usage-history} — every benefit usage
     * registered at any ally the current user is an active operator on.
     * The {@code AllyUser} pivot (V12) is N:M, so a single user can see
     * usages across multiple allies; the subquery handles that without
     * forcing the controller to enumerate them.
     *
     * <p>{@code bu.active = true} filters reversed / soft-deleted rows;
     * {@code au.active = true} filters revoked memberships in the
     * {@code AllyUser} pivot. The composite index
     * {@code idx_benefit_usages_ally_date (ally_id, usage_date DESC)} from
     * V24 backs the sort.</p>
     */
    @Query("""
            SELECT bu FROM BenefitUsage bu
            WHERE bu.active = true
              AND bu.ally.id IN (
                  SELECT au.ally.id FROM AllyUser au
                  WHERE au.user.uuid = :userUuid AND au.active = true
              )
            """)
    Page<BenefitUsage> findByAllyUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);

    /**
     * Same scope as {@link #findByAllyUserUuid}, filtered by the member's
     * document number, full name, or member UUID — the counter operator's
     * "search" box on the Consumos screen. The UUID branch is forward-looking
     * for a future member-card QR (encodes {@code memberUuid}); a scan lands
     * here as an exact match instead of the substring match used for the
     * other two fields. {@code :search} is matched case-insensitively;
     * blank/null callers should use the unfiltered overload instead.
     */
    @Query("""
            SELECT bu FROM BenefitUsage bu
            WHERE bu.active = true
              AND bu.ally.id IN (
                  SELECT au.ally.id FROM AllyUser au
                  WHERE au.user.uuid = :userUuid AND au.active = true
              )
              AND (
                  LOWER(bu.membership.member.person.documentNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(bu.membership.member.person.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(CAST(bu.membership.member.uuid AS string)) = LOWER(:search)
              )
            """)
    Page<BenefitUsage> findByAllyUserUuidAndSearch(@Param("userUuid") UUID userUuid, @Param("search") String search, Pageable pageable);

    /**
     * Powers {@code GET /v1/me/usage-history} (vertical-9) — every benefit usage
     * on the caller's own membership history. Walks {@code user.person →
     * member.person → membership → benefit_usage} so a member sees their whole
     * consumption feed regardless of which ally registered it. {@code bu.active}
     * filters reversed / soft-deleted rows; the caller's {@link Pageable}
     * supplies the sort (default {@code usageDate DESC}).
     */
    @Query("""
            SELECT bu FROM BenefitUsage bu
            LEFT JOIN bu.beneficiary b
            LEFT JOIN b.user bun
            WHERE bu.active = true
              AND (bu.membership.member.person.id =
                       (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)
                   OR bun.uuid = :userUuid)
            """)
    Page<BenefitUsage> findByMemberUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);

    /**
     * {@link #findByMemberUserUuid} narrowed by who consumed and by what — the
     * filters of the affiliate's "Mis consumos" screen. Every filter is optional:
     * {@code holderOnly} keeps the holder's own usages, {@code beneficiaryUuid}
     * one beneficiary's, {@code productUuid} one catalog product (across allies)
     * and {@code productCategoryUuid} one category. LEFT JOINs so the holder's
     * rows (no beneficiary) and uncatalogued rows (no product) are not dropped.
     */
    @Query("""
            SELECT bu FROM BenefitUsage bu
            LEFT JOIN bu.beneficiary b
            LEFT JOIN b.user bun
            LEFT JOIN bu.allyProduct ap
            LEFT JOIN ap.product p
            WHERE bu.active = true
              AND (bu.membership.member.person.id =
                       (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)
                   OR bun.uuid = :userUuid)
              AND (:holderOnly = false OR b IS NULL)
              AND (:beneficiaryUuid IS NULL OR b.uuid = :beneficiaryUuid)
              AND (:productUuid IS NULL OR p.uuid = :productUuid)
              AND (:productCategoryUuid IS NULL OR bu.productCategory.uuid = :productCategoryUuid)
            """)
    Page<BenefitUsage> findByMemberUserUuidFiltered(@Param("userUuid") UUID userUuid,
                                                    @Param("holderOnly") boolean holderOnly,
                                                    @Param("beneficiaryUuid") UUID beneficiaryUuid,
                                                    @Param("productUuid") UUID productUuid,
                                                    @Param("productCategoryUuid") UUID productCategoryUuid,
                                                    Pageable pageable);

    /**
     * The affiliate's consumption grouped by service — count and last date per
     * product (or per category for uncatalogued usages). Same person filters as
     * {@link #findByMemberUserUuidFiltered}; only live (REGISTERED) usages.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.benefit.dto.UsageServiceSummaryDto(
                       p.uuid, p.name, pc.name, COUNT(bu), MAX(bu.usageDate))
            FROM BenefitUsage bu
            JOIN bu.productCategory pc
            LEFT JOIN bu.beneficiary b
            LEFT JOIN b.user bun
            LEFT JOIN bu.allyProduct ap
            LEFT JOIN ap.product p
            WHERE bu.active = true
              AND bu.status = 'REGISTERED'
              AND (bu.membership.member.person.id =
                       (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)
                   OR bun.uuid = :userUuid)
              AND (:holderOnly = false OR b IS NULL)
              AND (:beneficiaryUuid IS NULL OR b.uuid = :beneficiaryUuid)
            GROUP BY p.uuid, p.name, pc.name
            ORDER BY COUNT(bu) DESC, MAX(bu.usageDate) DESC
            """)
    List<UsageServiceSummaryDto> summarizeByServiceForMemberUser(@Param("userUuid") UUID userUuid,
                                                                 @Param("holderOnly") boolean holderOnly,
                                                                 @Param("beneficiaryUuid") UUID beneficiaryUuid);

    /**
     * The affiliate's live (REGISTERED) consumption per person — one row for
     * the holder ({@code beneficiaryUuid} null) and one per beneficiary that
     * used something. People without usages are simply absent.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.benefit.dto.UsagePersonSummaryDto(
                       b.uuid, COUNT(bu), MAX(bu.usageDate))
            FROM BenefitUsage bu
            LEFT JOIN bu.beneficiary b
            LEFT JOIN b.user bun
            WHERE bu.active = true
              AND bu.status = 'REGISTERED'
              AND (bu.membership.member.person.id =
                       (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)
                   OR bun.uuid = :userUuid)
            GROUP BY b.uuid
            """)
    List<UsagePersonSummaryDto> summarizeByPersonForMemberUser(@Param("userUuid") UUID userUuid);

    /**
     * One usage of the caller's own membership history — same scope as
     * {@link #findByMemberUserUuid}. Empty when the usage belongs to someone
     * else, so the rating endpoint answers 404 instead of leaking existence.
     */
    @Query("""
            SELECT bu FROM BenefitUsage bu
            LEFT JOIN bu.beneficiary b
            LEFT JOIN b.user bun
            WHERE bu.uuid = :usageUuid
              AND bu.active = true
              AND (bu.membership.member.person.id =
                       (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)
                   OR bun.uuid = :userUuid)
            """)
    Optional<BenefitUsage> findByUuidForMemberUser(@Param("usageUuid") UUID usageUuid,
                                                   @Param("userUuid") UUID userUuid);

    // ─── Rating aggregates (V201) ─────────────────────────────────────────────
    // Recomputed from scratch on every rating: a full AVG over one ally / offer /
    // product is cheap (partial indexes) and can't drift the way an incremental
    // update would. An aggregate over zero rows yields AVG = NULL, COUNT = 0,
    // which is exactly the "no ratings" state. Only live usages count.

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE allies a
            SET rating_avg = s.avg, rating_count = s.cnt
            FROM (SELECT ROUND(AVG(ally_rating), 2) AS avg, COUNT(*) AS cnt
                  FROM benefit_usages
                  WHERE ally_id = :allyId AND ally_rating IS NOT NULL
                    AND is_active AND status = 'REGISTERED') s
            WHERE a.allies_id = :allyId
            """, nativeQuery = true)
    void refreshAllyRating(@Param("allyId") Long allyId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE ally_products ap
            SET rating_avg = s.avg, rating_count = s.cnt
            FROM (SELECT ROUND(AVG(product_rating), 2) AS avg, COUNT(*) AS cnt
                  FROM benefit_usages
                  WHERE ally_product_id = :allyProductId AND product_rating IS NOT NULL
                    AND is_active AND status = 'REGISTERED') s
            WHERE ap.ally_products_id = :allyProductId
            """, nativeQuery = true)
    void refreshAllyProductRating(@Param("allyProductId") Long allyProductId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE products p
            SET rating_avg = s.avg, rating_count = s.cnt
            FROM (SELECT ROUND(AVG(bu.product_rating), 2) AS avg, COUNT(*) AS cnt
                  FROM benefit_usages bu
                           JOIN ally_products ap ON ap.ally_products_id = bu.ally_product_id
                  WHERE ap.product_id = :productId AND bu.product_rating IS NOT NULL
                    AND bu.is_active AND bu.status = 'REGISTERED') s
            WHERE p.products_id = :productId
            """, nativeQuery = true)
    void refreshProductRating(@Param("productId") Long productId);

    /**
     * Consumo agregado de una membresía en un período. {@code allyUuid} nulo
     * agrega todos los aliados — un programa de fidelidad puede ser global o
     * de un solo comercio.
     *
     * <p>{@code COALESCE} sobre el SUM porque un período sin consumos devuelve
     * null y el llamador compara contra un umbral. Los usos anteriores a V158
     * no tienen monto y suman 0, pero sí cuentan en {@code count} — por eso el
     * conteo no se puede derivar de la suma.</p>
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.benefit.dto.ConsumptionTotalsDto(
                       COALESCE(SUM(bu.consumptionAmount), 0), COUNT(bu))
            FROM BenefitUsage bu
            WHERE bu.active = true
              AND bu.status = 'REGISTERED'
              AND bu.membership.uuid = :membershipUuid
              AND (:allyUuid IS NULL OR bu.ally.uuid = :allyUuid)
              AND bu.usageDate BETWEEN :from AND :to
            """)
    ConsumptionTotalsDto totalsForMembership(@Param("membershipUuid") UUID membershipUuid,
                                             @Param("allyUuid") UUID allyUuid,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);
}
