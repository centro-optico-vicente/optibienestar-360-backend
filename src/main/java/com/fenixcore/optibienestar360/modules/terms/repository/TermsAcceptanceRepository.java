package com.fenixcore.optibienestar360.modules.terms.repository;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Acceptances are unique per (user, version) without agreement — AFILIADO /
 * ASESOR and unlinked legacy ALIADO rows — and per (user, version, agreement)
 * for ALIADO (V189 partial unique indexes); the two {@code exists…} lookups
 * mirror those two keys.
 */
@Transactional(readOnly = true)
public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, Long> {

    boolean existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(Long userId, Long termsVersionId);

    boolean existsByUserIdAndTermsVersionIdAndAllyAgreementId(Long userId, Long termsVersionId, Long allyAgreementId);

    /** Latest acceptance of an ALIADO agreement — the "previous" text when its template gets a new version. */
    @EntityGraph(attributePaths = {"termsVersion", "allyAgreement"})
    Optional<TermsAcceptance> findFirstByUserIdAndAllyAgreementIdOrderByAcceptedAtDescIdDesc(Long userId,
                                                                                            Long allyAgreementId);

    /** Latest acceptance of any agreement of an ally — fallback when the ally got a new agreement. */
    @EntityGraph(attributePaths = {"termsVersion", "allyAgreement"})
    Optional<TermsAcceptance> findFirstByUserIdAndAllyAgreementAllyIdOrderByAcceptedAtDescIdDesc(Long userId,
                                                                                                Long allyId);

    /** Latest unlinked acceptance of a version (pre-V189 ALIADO rows that could not be linked). */
    @EntityGraph(attributePaths = {"termsVersion"})
    Optional<TermsAcceptance> findFirstByUserIdAndTermsVersionIdAndAllyAgreementIsNullOrderByAcceptedAtDescIdDesc(
            Long userId, Long termsVersionId);

    /** Latest acceptance of a type without agreement — AFILIADO / ASESOR "previous" text. */
    @EntityGraph(attributePaths = {"termsVersion"})
    Optional<TermsAcceptance> findFirstByUserIdAndTermsVersionTermTypeAndAllyAgreementIsNullOrderByAcceptedAtDescIdDesc(
            Long userId, TermType termType);

    /** Backs {@code GET /v1/me/terms/acceptances}: the user's acceptances, newest first. */
    @EntityGraph(attributePaths = {"termsVersion", "allyAgreement", "allyAgreement.ally"})
    List<TermsAcceptance> findByUserIdOrderByAcceptedAtDescIdDesc(Long userId);
}
