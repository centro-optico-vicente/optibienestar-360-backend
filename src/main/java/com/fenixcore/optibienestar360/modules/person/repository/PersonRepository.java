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
}
