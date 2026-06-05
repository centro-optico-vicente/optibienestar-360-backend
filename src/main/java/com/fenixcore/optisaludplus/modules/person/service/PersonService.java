package com.fenixcore.optisaludplus.modules.person.service;

import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.person.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Find-or-create entry-point for the persons hub. Any service that creates a
 * role (User, Member, Beneficiary, Promoter, AllyUser) should resolve the
 * Person via this service first — if a row with the same {@code (document_type,
 * document_number)} already exists, that row is reused, so a single human
 * appearing in multiple roles never gets duplicated.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonService {

    private final PersonRepository repository;

    /**
     * Look up an existing person by cédula. If none exists, persist a new one
     * with the data carried by {@code seed} (the seed's persons_id is ignored
     * — the new row gets a fresh identity).
     *
     * <p>When the row already exists, {@code seed} is NOT used to update its
     * fields — assume the existing data is canonical. Updates to existing
     * persons go through {@link #update(Person)} explicitly to keep the
     * find-or-create semantics predictable.</p>
     */
    @Transactional
    public Person findOrCreate(Person seed) {
        if (seed.getDocumentType() == null || seed.getDocumentNumber() == null) {
            throw new IllegalArgumentException("person.document.required");
        }
        Optional<Person> existing = repository.findByDocumentTypeAndDocumentNumber(
                seed.getDocumentType(), seed.getDocumentNumber());
        return existing.orElseGet(() -> repository.save(seed));
    }

    @Transactional
    public Person update(Person managed) {
        // Managed entity → dirty-check on tx commit. Method exists for clarity
        // at call sites that want to express "I'm updating this person".
        return managed;
    }
}
