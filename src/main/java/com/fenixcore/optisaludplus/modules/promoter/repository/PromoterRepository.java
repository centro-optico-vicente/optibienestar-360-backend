package com.fenixcore.optisaludplus.modules.promoter.repository;

import com.fenixcore.optisaludplus.modules.promoter.entity.Promoter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PromoterRepository extends JpaRepository<Promoter, Long>,
        JpaSpecificationExecutor<Promoter> {

    Optional<Promoter> findByUuid(UUID uuid);

    /** Natural-key lookup — services that need "the INSTITUCION promoter" resolve by code. */
    Optional<Promoter> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    /** Cross-table uniqueness pre-check — see V27 referrals deferred-decision item on collisions. */
    boolean existsByUserId(Long userId);
}
