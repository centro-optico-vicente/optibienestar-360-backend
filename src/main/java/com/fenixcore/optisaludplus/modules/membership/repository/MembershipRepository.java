package com.fenixcore.optisaludplus.modules.membership.repository;

import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MembershipRepository extends JpaRepository<Membership, Long>,
        JpaSpecificationExecutor<Membership> {

    Optional<Membership> findByUuid(UUID uuid);

    /** Pre-check before INSERT — the V21 partial UNIQUE on (member_id) WHERE is_active=TRUE only allows one. */
    boolean existsByMemberIdAndActiveTrue(Long memberId);

    /** Currently-active subscription of the member. */
    Optional<Membership> findFirstByMemberIdAndActiveTrue(Long memberId);

    /** History of subscriptions for a member, newest enrollment first. */
    List<Membership> findByMemberIdOrderByEnrolledAtDesc(Long memberId);
}
