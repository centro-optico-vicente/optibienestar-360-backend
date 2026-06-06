package com.fenixcore.optisaludplus.modules.member.repository;

import com.fenixcore.optisaludplus.modules.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Resolve the member of the user identified by JWT subject. Backs
     * {@code GET /v1/me/member} — walks the {@code user.person} link to find
     * the matching {@link Member} in a single query without loading the User.
     */
    @Query("SELECT m FROM Member m WHERE m.person.id = " +
           "(SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)")
    Optional<Member> findByUserUuid(@Param("userUuid") UUID userUuid);
}
