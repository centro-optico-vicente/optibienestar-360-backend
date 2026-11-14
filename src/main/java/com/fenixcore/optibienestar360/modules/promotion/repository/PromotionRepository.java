package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByUuid(UUID uuid);

    List<Promotion> findByCampaignAndActiveTrueOrderByCreatedAtAsc(Campaign campaign);

    /**
     * Promotions that can be applied right now (hub ADR 0038 §3): active, in
     * an enabled campaign whose lifecycle is {@code status} (ACTIVE) and whose
     * window contains {@code now} — an open-ended campaign has no end, and a
     * passed end is not offered even before the daily job closes it.
     */
    @Query("""
            select p from Promotion p join fetch p.campaign c
            where p.active = true and c.active = true and c.enabled = true
              and c.lifecycleStatus = :status
              and c.startsAt <= :now and (c.endsAt is null or c.endsAt > :now)
            order by c.startsAt, p.name
            """)
    List<Promotion> findOfferedInStatus(@Param("now") OffsetDateTime now,
                                        @Param("status") Campaign.LifecycleStatus status);

    default List<Promotion> findOffered(OffsetDateTime now) {
        return findOfferedInStatus(now, Campaign.LifecycleStatus.ACTIVE);
    }

    /** The campaign of a live promotion, read without loading either entity (so both can be row-locked fresh). */
    @Query("select p.campaign.id from Promotion p where p.uuid = :uuid and p.active = true")
    Optional<Long> findCampaignIdByUuid(@Param("uuid") UUID uuid);

    /** Row lock ({@code SELECT ... FOR UPDATE}) taken after the campaign's before touching the counters (hub ADR 0038 §4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.uuid = :uuid")
    Optional<Promotion> findForUpdateByUuid(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id")
    Optional<Promotion> findForUpdateById(@Param("id") Long id);
}
