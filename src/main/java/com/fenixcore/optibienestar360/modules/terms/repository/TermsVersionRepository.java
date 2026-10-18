package com.fenixcore.optibienestar360.modules.terms.repository;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {

    Optional<TermsVersion> findByUuid(UUID uuid);

    /**
     * "Current version" of a type's unscoped stream — latest {@code validFrom <= at}
     * with no ALIADO scope (V183). For AFILIADO / PROMOTOR that is the only stream;
     * for ALIADO it is the general template.
     */
    Optional<TermsVersion> findFirstByTermTypeAndAllyTypeIsNullAndSegmentIsNullAndAllyIsNullAndValidFromLessThanEqualOrderByValidFromDesc(
            TermType termType, Instant at);

    /** Not-yet-vigente versions of a type, every stream — enforces "only one scheduled per stream". */
    List<TermsVersion> findByTermTypeAndValidFromGreaterThan(TermType termType, Instant at);

    @EntityGraph(attributePaths = {"allyType", "ally"})
    List<TermsVersion> findByTermTypeOrderByValidFromDesc(TermType termType);

    @EntityGraph(attributePaths = {"allyType", "ally"})
    List<TermsVersion> findAllByOrderByValidFromDesc();
}
