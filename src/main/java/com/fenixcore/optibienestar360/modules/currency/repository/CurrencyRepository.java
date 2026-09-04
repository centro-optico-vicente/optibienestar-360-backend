package com.fenixcore.optibienestar360.modules.currency.repository;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByUuid(UUID uuid);

    /** Natural-key lookup — services resolving a hardcoded ISO code (e.g. "USD") into the entity. */
    Optional<Currency> findByCode(String code);
}
