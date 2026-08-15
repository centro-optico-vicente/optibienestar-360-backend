package com.fenixcore.optibienestar360.modules.person.repository;

import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByUuid(UUID uuid);

    Optional<Person> findByDocumentTypeAndDocumentNumber(String documentType, String documentNumber);

    boolean existsByDocumentTypeAndDocumentNumber(String documentType, String documentNumber);

    /** Usage count for {@code Gender} delete/reactivation checks — see {@code GenderService}. */
    long countByGender_Uuid(UUID uuid);

    /** Usage count for {@code MaritalStatus} delete/reactivation checks — see {@code MaritalStatusService}. */
    long countByMaritalStatus_Uuid(UUID uuid);

    /** Usage count for {@code City} delete/reactivation checks — see {@code CityService}. */
    long countByCity_Uuid(UUID uuid);
}
