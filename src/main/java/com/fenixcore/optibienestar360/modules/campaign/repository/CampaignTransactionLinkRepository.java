package com.fenixcore.optibienestar360.modules.campaign.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
public interface CampaignTransactionLinkRepository extends JpaRepository<CampaignTransactionLink, Long> {

    List<CampaignTransactionLink> findByCampaign(Campaign campaign);

    Page<CampaignTransactionLink> findByCampaign(Campaign campaign, Pageable pageable);

    Optional<CampaignTransactionLink> findByCampaignAndPayment_Id(Campaign campaign, Long paymentId);

    Optional<CampaignTransactionLink> findByCampaignAndMembership_Id(Campaign campaign, Long membershipId);
}
