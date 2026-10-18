package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterPayoutAccount;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PromoterPayoutAccountRepository extends JpaRepository<PromoterPayoutAccount, Long> {

    Optional<PromoterPayoutAccount> findByUuid(UUID uuid);

    List<PromoterPayoutAccount> findByPromoterIdAndActiveTrue(Long promoterId, Sort sort);

    /** Current default(s) — at most one per V184 partial unique index. */
    List<PromoterPayoutAccount> findByPromoterIdAndActiveTrueAndDefaultAccountTrue(Long promoterId);

    boolean existsByPromoterIdAndActiveTrueAndDefaultAccountTrue(Long promoterId);
}
