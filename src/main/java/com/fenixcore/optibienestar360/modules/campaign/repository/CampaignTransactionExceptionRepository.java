package com.fenixcore.optibienestar360.modules.campaign.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CampaignTransactionExceptionRepository extends JpaRepository<CampaignTransactionException, Long> {

    List<CampaignTransactionException> findByCampaign(Campaign campaign);

    Page<CampaignTransactionException> findByCampaign(Campaign campaign, Pageable pageable);

    java.util.Optional<CampaignTransactionException> findByUuid(UUID uuid);
}
