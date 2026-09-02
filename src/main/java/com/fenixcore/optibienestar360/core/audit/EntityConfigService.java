package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.entity.EntityConfig;
import com.fenixcore.optibienestar360.core.audit.repository.EntityConfigRepository;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves per-entity behavior config (spec 16-audit.md §Aspecto AOP, paso 1
 * for the audit part). Backed by {@code entity_config} (V60, renamed+extended
 * by V80 from {@code audit_entity_config}), cached under {@code
 * "entity-config"} (60s TTL in prod, see {@code RedisCacheConfig}).
 *
 * <p><b>Audit enable/disable</b> — two layers, in order:</p>
 * <ol>
 *   <li>{@code system_configs}' global per-action {@link AuditMode} override
 *       (Decisión 8, split per action in V83) — {@code FORCE_ENABLED}/{@code
 *       FORCE_DISABLED} short-circuit everything below for that action; only
 *       {@code PER_ENTITY} falls through.</li>
 *   <li>{@code entity_config}'s per-entity {@code enabled} + per-action flag.</li>
 * </ol>
 *
 * <p>Fail-safe: a missing {@code entity_config} row behaves like
 * {@code enabled=false} for audit (skip + warn), never blocks the business
 * operation (Decisión 6); for {@link #getDefaultSort} it just means "no
 * configured default" — callers fall back to their own hard default
 * ({@code createdAt DESC}).</p>
 *
 * <p><b>Default sort</b> — {@link #getDefaultSort(String)} returns the
 * ordered {@code default_sort} column, empty when unconfigured. Callers
 * translate each field through their own {@code SortFieldValidator}
 * sortable-fields map, same as an explicit {@code ?sort=}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityConfigService {

	private final EntityConfigRepository entityConfigRepository;
	private final EntityConfigCache entityConfigCache;
	private final SystemConfigService systemConfigService;

	@Transactional(readOnly = true)
	public boolean isEnabled(String entityKey, AuditAction action) {
		AuditMode globalMode = switch (action) {
			case CREATE -> systemConfigService.getAuditCreateMode();
			case UPDATE -> systemConfigService.getAuditUpdateMode();
			case DELETE -> systemConfigService.getAuditDeleteMode();
		};
		if (globalMode == AuditMode.FORCE_DISABLED) {
			return false;
		}
		if (globalMode == AuditMode.FORCE_ENABLED) {
			return true;
		}

		EntityConfig config = findConfig(entityKey);
		if (config == null) {
			log.warn(
				"entity_config has no row for entity_key='{}' — skipping audit for {} (fail-safe)",
				entityKey,
				action
			);
			return false;
		}
		if (!config.isEnabled()) {
			return false;
		}
		return switch (action) {
			case CREATE -> config.isAuditCreate();
			case UPDATE -> config.isAuditUpdate();
			case DELETE -> config.isAuditDelete();
		};
	}

	@Transactional(readOnly = true)
	public boolean captureBeforeAfter(String entityKey) {
		EntityConfig config = findConfig(entityKey);
		return config != null && config.isCaptureBeforeAfter();
	}

	/**
	 * The configured default sort for {@code entityKey}, in request order.
	 * Empty (never {@code null}) when there's no row or no {@code
	 * default_sort} configured — the caller decides its own hard fallback.
	 */
	@Transactional(readOnly = true)
	public List<SortOrder> getDefaultSort(String entityKey) {
		EntityConfig config = findConfig(entityKey);
		return config != null && config.getDefaultSort() != null ? config.getDefaultSort() : List.of();
	}

	public EntityConfig findConfig(String entityKey) {
		return entityConfigCache.get(entityKey);
	}

	/** All rows for the admin config screen, alphabetical by display name. */
	@Transactional(readOnly = true)
	public List<EntityConfig> listAll() {
		return entityConfigRepository.findAllByOrderByDisplayNameAsc();
	}

	@Transactional
	public EntityConfig updateConfig(String entityKey, EntityConfig changes) {
		EntityConfig config = entityConfigRepository.findByEntityKey(entityKey)
				.orElseThrow(() -> new java.util.NoSuchElementException(
						"entity_config.entity_key not found: " + entityKey));

		config.setEnabled(changes.isEnabled());
		config.setAuditCreate(changes.isAuditCreate());
		config.setAuditUpdate(changes.isAuditUpdate());
		config.setAuditDelete(changes.isAuditDelete());
		config.setAuditReport(changes.isAuditReport());
		config.setCaptureBeforeAfter(changes.isCaptureBeforeAfter());
		config.setDefaultSort(changes.getDefaultSort());
		config.setNotes(changes.getNotes());

		EntityConfig saved = entityConfigRepository.save(config);
		entityConfigCache.evict(entityKey);
		return saved;
	}

	/**
	 * Registers a new entity. Every audit flag defaults to {@code true} and
	 * {@code defaultSort} starts unconfigured — same defaults migration V60
	 * seeds for its rows. {@code entity_key} uniqueness is enforced by the
	 * DB constraint (a duplicate surfaces as {@code DataIntegrityViolationException}
	 * → 409, same as everywhere else in the app).
	 */
	@Transactional
	public EntityConfig createConfig(String entityKey, String displayName, String tableName, String notes) {
		EntityConfig config = new EntityConfig();
		config.setEntityKey(entityKey);
		config.setDisplayName(displayName);
		config.setTableName(tableName);
		config.setNotes(notes);
		return entityConfigRepository.save(config);
	}

	@Transactional
	public void deleteConfig(String entityKey) {
		EntityConfig config = entityConfigRepository.findByEntityKey(entityKey)
			.orElseThrow(() -> new java.util.NoSuchElementException(
					"entity_config.entity_key not found: " + entityKey
				)
			)
		;
		entityConfigRepository.delete(config);
		entityConfigCache.evict(entityKey);
	}

}
