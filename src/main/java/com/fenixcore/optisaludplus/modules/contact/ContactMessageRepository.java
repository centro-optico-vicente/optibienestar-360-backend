package com.fenixcore.optisaludplus.modules.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    Optional<ContactMessage> findByUuid(UUID uuid);
}
