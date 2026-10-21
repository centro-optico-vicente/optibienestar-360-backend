package com.fenixcore.optibienestar360.modules.affiliation.repository;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AffiliationRequestRepository
        extends JpaRepository<AffiliationRequest, Long>, JpaSpecificationExecutor<AffiliationRequest> {

    @EntityGraph(attributePaths = {"plan", "promoter", "termsVersion", "reviewedBy", "user", "member", "membership"})
    Optional<AffiliationRequest> findByUuid(UUID uuid);

    /**
     * Row-locked read for the review transitions (update / approve / reject):
     * two reviewers acting at once queue on the row, so the second one sees
     * the status the first one left and a request is never approved twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AffiliationRequest r WHERE r.uuid = :uuid")
    Optional<AffiliationRequest> findForUpdateByUuid(@Param("uuid") UUID uuid);

    /** List rows carry the plan and advisor labels — both {@code @ManyToOne}, so paging stays in the database. */
    @Override
    @EntityGraph(attributePaths = {"plan", "promoter"})
    Page<AffiliationRequest> findAll(Specification<AffiliationRequest> spec, Pageable pageable);

    boolean existsByEmailIgnoreCaseAndStatus(String email, String status);

    boolean existsByDocumentTypeAndDocumentNumberAndStatus(String documentType, String documentNumber, String status);

    boolean existsByEmailIgnoreCaseAndStatusAndIdNot(String email, String status, Long id);

    boolean existsByDocumentTypeAndDocumentNumberAndStatusAndIdNot(String documentType, String documentNumber,
                                                                   String status, Long id);
}
