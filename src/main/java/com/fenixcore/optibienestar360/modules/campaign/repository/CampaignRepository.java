package com.fenixcore.optibienestar360.modules.campaign.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {

    Optional<Campaign> findByUuid(UUID uuid);

    /**
     * Row lock ({@code SELECT ... FOR UPDATE}) taken before a quota check or a
     * lifecycle transition (hub ADR 0038 §4): two simultaneous assignments
     * can't both take the last slot, and a close can't race an assignment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Campaign c WHERE c.id = :id")
    Optional<Campaign> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Campaign c WHERE c.uuid = :uuid")
    Optional<Campaign> findForUpdateByUuid(@Param("uuid") UUID uuid);

    /** Campaigns the END_DATE condition closes now: not closed yet, END_DATE configured and the end already passed. */
    @Query("""
            SELECT c FROM Campaign c
            WHERE c.lifecycleStatus IN :statuses
              AND c.closeOnEndDate = true
              AND c.endsAt IS NOT NULL AND c.endsAt <= :now
            """)
    List<Campaign> findDueForEndDateClose(@Param("statuses") Collection<Campaign.LifecycleStatus> statuses,
                                          @Param("now") OffsetDateTime now);
}
