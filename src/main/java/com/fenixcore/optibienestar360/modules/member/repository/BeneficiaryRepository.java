package com.fenixcore.optibienestar360.modules.member.repository;

import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long>,
        JpaSpecificationExecutor<Beneficiary> {

    Optional<Beneficiary> findByUuid(UUID uuid);

    List<Beneficiary> findByMemberIdAndActiveTrue(Long memberId, org.springframework.data.domain.Sort sort);

    /** Where else is this person a beneficiary? (validator + digital card flows). */
    List<Beneficiary> findByPersonIdAndActiveTrue(Long personId);

    /** Backs the readmission flow — UNIQUE(member_id, person_id) means we reactivate, never duplicate. */
    Optional<Beneficiary> findByMemberIdAndPersonId(Long memberId, Long personId);

    long countByMemberIdAndActiveTrue(Long memberId);

    /**
     * Usage check for {@code MembersService.countUsages} — ALL rows (active +
     * inactive). {@code Member.beneficiaries} has NO {@code cascade}/{@code
     * orphanRemoval} declared (plain {@code @OneToMany(mappedBy = "member")}),
     * so Hibernate does not cascade-delete beneficiaries with their parent —
     * these rows are a genuine hard-delete blocker, not an owned child to
     * exclude.
     */
    long countByMemberId(Long memberId);
}
