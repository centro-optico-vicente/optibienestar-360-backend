package com.fenixcore.optibienestar360.modules.person.repository;

import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense.VerificationStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PersonProfessionalLicenseRepository extends JpaRepository<PersonProfessionalLicense, Long> {

    Optional<PersonProfessionalLicense> findByUuid(UUID uuid);

    List<PersonProfessionalLicense> findByPerson_IdAndActiveTrue(Long personId, Sort sort);

    List<PersonProfessionalLicense> findByPerson_IdAndActiveTrueAndVerificationStatus(
            Long personId, VerificationStatus status, Sort sort);

    /** Live licenses only: a removed row must not block re-registering the number (V213 partial index). */
    boolean existsByLicensingBody_IdAndLicenseNumberIgnoreCaseAndActiveTrue(Long licensingBodyId, String licenseNumber);

    long countByLicensingBody_Uuid(UUID licensingBodyUuid);
}
