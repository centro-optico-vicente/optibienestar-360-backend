package com.fenixcore.optibienestar360.core.audit.repository;

import com.fenixcore.optibienestar360.core.audit.LoginSessionStatus;
import com.fenixcore.optibienestar360.core.audit.entity.LoginAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginAuditLogRepository
        extends JpaRepository<LoginAuditLog, Long>, JpaSpecificationExecutor<LoginAuditLog> {

    Optional<LoginAuditLog> findByUuid(UUID uuid);

    /**
     * Bulk expiry sweep (spec 16-audit.md §Login "Chequeo simple con
     * is_valid") — the only place that compares {@code session_expires_at}
     * against {@code now()}, run in background/batch by
     * {@code LoginSessionSweepJob} so the hot-path check in
     * {@code JwtAuthenticationFilter} stays a single boolean read.
     */
    @Modifying
    @Query("UPDATE LoginAuditLog l SET l.valid = false, l.sessionStatus = com.fenixcore.optibienestar360.core.audit.LoginSessionStatus.EXPIRED "
            + "WHERE l.valid = true AND l.sessionStatus = com.fenixcore.optibienestar360.core.audit.LoginSessionStatus.ACTIVE "
            + "AND l.sessionExpiresAt < :now")
    int expireStaleSessions(@Param("now") Instant now);

    long countBySessionStatus(LoginSessionStatus sessionStatus);
}
