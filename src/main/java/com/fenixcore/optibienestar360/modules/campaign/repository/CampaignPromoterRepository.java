package com.fenixcore.optibienestar360.modules.campaign.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignPromoter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface CampaignPromoterRepository extends JpaRepository<CampaignPromoter, Long> {

    List<CampaignPromoter> findByCampaign(Campaign campaign);

    void deleteByCampaign(Campaign campaign);
}
