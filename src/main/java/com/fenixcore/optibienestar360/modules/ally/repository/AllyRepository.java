package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyRepository extends JpaRepository<Ally, Long>, JpaSpecificationExecutor<Ally> {

    Optional<Ally> findByUuid(UUID uuid);

    Optional<Ally> findByTaxDocumentTypeAndTaxDocumentNumber(String taxDocumentType,
                                                             String taxDocumentNumber);

    boolean existsByTaxDocumentTypeAndTaxDocumentNumber(String taxDocumentType,
                                                        String taxDocumentNumber);

    /** Usage count for {@code AllyType} delete/reactivation checks — see {@code AllyTypeService}. */
    long countByAllyType_Uuid(UUID uuid);

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
