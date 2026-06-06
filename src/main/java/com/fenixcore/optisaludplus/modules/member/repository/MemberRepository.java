package com.fenixcore.optisaludplus.modules.member.repository;

import com.fenixcore.optisaludplus.modules.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

    Optional<Member> findByUuid(UUID uuid);

    /** "Is this person already enrolled as a Member?" — backs the dedup guard at affiliation. */
    boolean existsByPersonId(Long personId);

    /** Used by the validator + the digital card flow to resolve a member from their cédula. */
    Optional<Member> findByPersonId(Long personId);
}
