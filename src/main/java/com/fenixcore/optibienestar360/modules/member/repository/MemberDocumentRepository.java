package com.fenixcore.optibienestar360.modules.member.repository;

import com.fenixcore.optibienestar360.modules.member.entity.MemberDocument;
import com.fenixcore.optibienestar360.modules.member.entity.MemberDocument.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MemberDocumentRepository extends JpaRepository<MemberDocument, Long> {

    Optional<MemberDocument> findByUuid(UUID uuid);

    List<MemberDocument> findByMemberIdAndActiveTrue(Long memberId);

    /** Single-upload-per-type guard for the upload flow. */
    Optional<MemberDocument> findFirstByMemberIdAndDocumentTypeAndActiveTrue(
            Long memberId, DocumentType documentType);

    long countByMemberIdAndActiveTrue(Long memberId);

    /** Usage check for {@code MembersService.countUsages} — ALL rows (active + inactive), see {@code BeneficiaryRepository.countByMemberId}. */
    long countByMemberId(Long memberId);
}
