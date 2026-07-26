package com.fenixcore.optibienestar360.modules.card.repository;

import com.fenixcore.optibienestar360.modules.card.entity.DigitalCardRow;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only access to {@code digital_cards_view} (V39). Extends the base
 * {@link Repository} (not {@code JpaRepository}) so no write/delete methods
 * are exposed on the immutable view.
 */
@Transactional(readOnly = true)
public interface DigitalCardRepository extends Repository<DigitalCardRow, UUID> {

    /** The card of the affiliate owning the given user account (1:1 person↔user). */
    Optional<DigitalCardRow> findByUserUuid(UUID userUuid);
}
