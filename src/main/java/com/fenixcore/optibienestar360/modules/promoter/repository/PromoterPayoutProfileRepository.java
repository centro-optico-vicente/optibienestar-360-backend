package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterPayoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PromoterPayoutProfileRepository extends JpaRepository<PromoterPayoutProfile, Long> {

    Optional<PromoterPayoutProfile> findByUuid(UUID uuid);

    /** 1:1 per V182 UNIQUE(promoter_id) — at most one row. */
    Optional<PromoterPayoutProfile> findByPromoterId(Long promoterId);
}
