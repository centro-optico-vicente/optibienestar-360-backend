package com.fenixcore.optibienestar360.modules.member.repository;

import com.fenixcore.optibienestar360.modules.member.entity.MemberPromoterAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Insert-only audit log of member↔promoter reassignments (V35). No specification
 * executor — the only writes are appends from
 * {@code MemberPromoterService.assign}; reads are the per-member history.
 */
@Transactional(readOnly = true)
public interface MemberPromoterAssignmentRepository extends JpaRepository<MemberPromoterAssignment, Long> {

    /** Reassignment history of a member, newest first. */
    List<MemberPromoterAssignment> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    /** Reassignment history of a member looked up by its public UUID, newest first. */
    List<MemberPromoterAssignment> findByMember_UuidOrderByCreatedAtDesc(UUID memberUuid);
}
