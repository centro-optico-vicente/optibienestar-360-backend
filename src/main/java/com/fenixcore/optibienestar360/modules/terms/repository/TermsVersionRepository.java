package com.fenixcore.optibienestar360.modules.terms.repository;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {

    Optional<TermsVersion> findByUuid(UUID uuid);

    /** "Current version" lookup — latest {@code validFrom <= at} for a type. Backed by {@code idx_terms_versions_type_valid_from}. */
    Optional<TermsVersion> findFirstByTermTypeAndValidFromLessThanEqualOrderByValidFromDesc(TermType termType, Instant at);

    /** The one not-yet-vigente version of a type, if any — enforces "only one scheduled at a time". */
    Optional<TermsVersion> findFirstByTermTypeAndValidFromGreaterThanOrderByValidFromAsc(TermType termType, Instant at);

    List<TermsVersion> findByTermTypeOrderByValidFromDesc(TermType termType);

    List<TermsVersion> findAllByOrderByValidFromDesc();
}
