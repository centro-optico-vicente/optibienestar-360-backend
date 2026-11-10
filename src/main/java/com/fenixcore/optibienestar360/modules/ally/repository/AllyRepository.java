package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyRepository extends JpaRepository<Ally, Long>, JpaSpecificationExecutor<Ally> {

    Optional<Ally> findByUuid(UUID uuid);

    /**
     * Registrations still in progress (never sent to review) of the given channels that nobody touched since
     * {@code cutoff} and that were not retained — the abandoned-draft cleanup.
     */
    List<Ally> findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
            Ally.RegistrationStatus status, java.util.Collection<Ally.RegistrationChannel> channels,
            java.time.Instant cutoff);

    /**
     * Row lock for the ally request transitions (hub ADR 0036): two reviewers
     * or a double click can't approve / reject the same request twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Ally a WHERE a.uuid = :uuid")
    Optional<Ally> findForUpdateByUuid(@Param("uuid") UUID uuid);

    /** Duplicate check of a professional request: the person's other B2P sites. */
    List<Ally> findByHolderPersonIdAndSegment(Long personId,
            com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment segment);

    Optional<Ally> findByReferralCode(String referralCode);

    Optional<Ally> findByTaxDocumentTypeAndTaxDocumentNumber(String taxDocumentType,
                                                             String taxDocumentNumber);

    boolean existsByTaxDocumentTypeAndTaxDocumentNumber(String taxDocumentType,
                                                        String taxDocumentNumber);

    /**
     * Usage count for {@code AllyType} delete/reactivation checks — see
     * {@code AllyTypeService}. A derived {@code countBy} cannot traverse a
     * {@code @ManyToMany} collection cleanly, so this is a plain join count.
     */
    @Query("select count(a) from Ally a join a.allyTypes t where t.uuid = :uuid")
    long countByAllyTypes_Uuid(@Param("uuid") UUID uuid);

    /** Usage count for {@code City} delete/reactivation checks — see {@code CityService}. */
    long countByCity_Uuid(UUID uuid);

    /**
     * Usage count for {@code Profession} delete/reactivation checks — see
     * {@code ProfessionService}. A derived {@code countBy} cannot traverse a
     * {@code @ManyToMany} collection cleanly, so this is a plain join count.
     */
    @Query("select count(a) from Ally a join a.professions s where s.uuid = :uuid")
    long countByProfessions_Uuid(@Param("uuid") UUID uuid);
}
