package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByUuid(UUID uuid);

    List<Promotion> findByCampaignAndActiveTrueOrderByCreatedAtAsc(Campaign campaign);
}
