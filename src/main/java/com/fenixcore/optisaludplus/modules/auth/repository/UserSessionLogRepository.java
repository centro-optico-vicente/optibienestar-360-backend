package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.UserSessionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionLogRepository extends JpaRepository<UserSessionLog, Long> {

    Optional<UserSessionLog> findByJtiAndLogoutAtIsNull(String jti);
}
