package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.entity.AuditEntityConfig;
import com.fenixcore.optibienestar360.core.audit.repository.AuditEntityConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate bean so {@code @Cacheable} actually goes through the Spring proxy —
 * {@link AuditEntityConfigService} calling this as a collaborator (not via
 * self-invocation) is what makes the cache annotations take effect.
 */
@Component
@RequiredArgsConstructor
public class AuditEntityConfigCache {

    private final AuditEntityConfigRepository auditEntityConfigRepository;

    @Cacheable(value = "audit-config", key = "#entityKey")
    @Transactional(readOnly = true)
    public AuditEntityConfig get(String entityKey) {
        return auditEntityConfigRepository.findByEntityKey(entityKey).orElse(null);
    }

    @CacheEvict(value = "audit-config", key = "#entityKey")
    public void evict(String entityKey) {
        // no-op body — the eviction happens via the annotation
    }
}
