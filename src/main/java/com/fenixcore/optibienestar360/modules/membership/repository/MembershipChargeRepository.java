package com.fenixcore.optibienestar360.modules.membership.repository;

import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Transactional(readOnly = true)
public interface MembershipChargeRepository extends JpaRepository<MembershipCharge, Long>,
        JpaSpecificationExecutor<MembershipCharge> {

    /** Idempotency check behind {@code MembershipChargeService.ensureChargeForPeriod} (V153 unique constraint). */
    Optional<MembershipCharge> findByMembership_IdAndPeriodStart(Long membershipId, LocalDate periodStart);

    boolean existsByMembership_IdAndPeriodStart(Long membershipId, LocalDate periodStart);
}
