package com.fenixcore.optibienestar360.modules.auth.repository;

import com.fenixcore.optibienestar360.modules.auth.entity.UserSessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
public interface UserSessionLogRepository extends JpaRepository<UserSessionLog, Long> {

    // ─── By jti (natural session identifier) ────────────────────────────────
    Optional<UserSessionLog> findByJtiAndLogoutAtIsNull(String jti);

    // ─── By user FK ─────────────────────────────────────────────────────────
    List<UserSessionLog> findByUserId(Long userId);

    // Open (not-yet-logged-out) sessions of a user — basis for enforcing
    // SecurityPolicy.maxConcurrentSessions and for "revoke all sessions".
    List<UserSessionLog> findByUserIdAndLogoutAtIsNull(Long userId);
}
