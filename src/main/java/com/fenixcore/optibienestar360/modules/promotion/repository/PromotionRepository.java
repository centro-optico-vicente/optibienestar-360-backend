package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByUuid(UUID uuid);

    List<Promotion> findByCampaignAndActiveTrueOrderByCreatedAtAsc(Campaign campaign);

    /** Promotions that can be applied right now: active, in an enabled campaign whose window contains {@code now}. */
    @Query("""
            select p from Promotion p join fetch p.campaign c
            where p.active = true and c.active = true and c.enabled = true
              and c.startsAt <= :now and c.endsAt >= :now
            order by c.startsAt, p.name
            """)
    List<Promotion> findOffered(@Param("now") OffsetDateTime now);
}
