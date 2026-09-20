package com.fenixcore.optibienestar360.modules.campaign.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignPromoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
public interface CampaignPromoterRepository extends JpaRepository<CampaignPromoter, Long> {

    List<CampaignPromoter> findByCampaign(Campaign campaign);

    Page<CampaignPromoter> findByCampaign(Campaign campaign, Pageable pageable);

    Optional<CampaignPromoter> findByCampaignAndPromoter(Campaign campaign, Promoter promoter);

    boolean existsByCampaignAndPromoter(Campaign campaign, Promoter promoter);

    void deleteByCampaign(Campaign campaign);

    void deleteByCampaignAndPromoter(Campaign campaign, Promoter promoter);
}
