package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.entity.EntityConfig;
import com.fenixcore.optibienestar360.core.audit.repository.EntityConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate bean so {@code @Cacheable} actually goes through the Spring proxy —
 * {@link EntityConfigService} calling this as a collaborator (not via
 * self-invocation) is what makes the cache annotations take effect.
 */
@Component
@RequiredArgsConstructor
public class EntityConfigCache {

	private final EntityConfigRepository entityConfigRepository;

	@Cacheable(value = "entity-config", key = "#entityKey")
	@Transactional(readOnly = true)
	public EntityConfig get(String entityKey) {
		return entityConfigRepository.findByEntityKey(entityKey).orElse(null);
	}

	@CacheEvict(value = "entity-config", key = "#entityKey")
	public void evict(String entityKey) {
		// no-op body — the eviction happens via the annotation
	}

}
