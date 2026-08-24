package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.repository.LoginAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Separate bean solely for the {@code @Cacheable}/{@code @CacheEvict}
 * methods — same self-invocation pitfall as {@code AuditEntityConfigCache}:
 * calling these from within {@code LoginAuditService} itself would bypass
 * the Spring proxy and silently skip caching.
 */
@Component
@RequiredArgsConstructor
public class LoginSessionValidityCache {

    private final LoginAuditLogRepository loginAuditLogRepository;

    @Cacheable(value = "login-session", key = "#sessionId")
    public boolean isValid(UUID sessionId) {
        return loginAuditLogRepository.findByUuid(sessionId)
                .map(com.fenixcore.optibienestar360.core.audit.entity.LoginAuditLog::isValid)
                .orElse(false);
    }

    @CacheEvict(value = "login-session", key = "#sessionId")
    public void evict(UUID sessionId) {
    }
}
