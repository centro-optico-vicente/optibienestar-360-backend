package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.entity.EntityConfig;
import com.fenixcore.optibienestar360.core.audit.repository.EntityConfigRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityConfigServiceTest {

	@Mock
	private EntityConfigRepository entityConfigRepository;

	@Mock
	private EntityConfigCache entityConfigCache;

	@Mock
	private SystemConfigService systemConfigService;

	private EntityConfigService service;

	private EntityConfig sampleConfig;

	@BeforeEach
	void setUp() {
		service = new EntityConfigService(entityConfigRepository, entityConfigCache, systemConfigService);

		sampleConfig = new EntityConfig();
		sampleConfig.setEntityKey("ally");
		sampleConfig.setEnabled(true);
		sampleConfig.setAuditCreate(true);
		sampleConfig.setAuditUpdate(true);
		sampleConfig.setAuditDelete(false);
		sampleConfig.setCaptureBeforeAfter(false);

		// Per-action global overrides default to PER_ENTITY unless a test overrides them.
		lenient().when(systemConfigService.getAuditCreateMode()).thenReturn(AuditMode.PER_ENTITY);
		lenient().when(systemConfigService.getAuditUpdateMode()).thenReturn(AuditMode.PER_ENTITY);
		lenient().when(systemConfigService.getAuditDeleteMode()).thenReturn(AuditMode.PER_ENTITY);
		lenient().when(systemConfigService.getCaptureBeforeAfterMode()).thenReturn(AuditMode.PER_ENTITY);
	}

	@Test
	@DisplayName("isEnabled falls through to the per-entity flag for the matching action when the global mode is PER_ENTITY")
	void isEnabledFallsThroughToEntityFlagPerAction() {
		when(entityConfigCache.get("ally")).thenReturn(sampleConfig);

		assertTrue(service.isEnabled("ally", AuditAction.CREATE));
		assertTrue(service.isEnabled("ally", AuditAction.UPDATE));
		assertFalse(service.isEnabled("ally", AuditAction.DELETE));
	}

	@Test
	@DisplayName("isEnabled(DELETE) ignores the per-entity flag when the global delete mode is FORCE_DISABLED")
	void isEnabledForceDisabledOverridesEntityFlag() {
		when(systemConfigService.getAuditDeleteMode()).thenReturn(AuditMode.FORCE_DISABLED);

		assertFalse(service.isEnabled("ally", AuditAction.DELETE));
		// CREATE/UPDATE global modes are untouched (still PER_ENTITY) — the override is per action, not entity-wide.
		when(entityConfigCache.get("ally")).thenReturn(sampleConfig);
		assertTrue(service.isEnabled("ally", AuditAction.CREATE));
	}

	@Test
	@DisplayName("isEnabled(DELETE) audits despite a disabled entity flag when the global delete mode is FORCE_ENABLED")
	void isEnabledForceEnabledOverridesEntityFlag() {
		when(systemConfigService.getAuditDeleteMode()).thenReturn(AuditMode.FORCE_ENABLED);

		assertTrue(service.isEnabled("ally", AuditAction.DELETE));
	}

	@Test
	@DisplayName("captureBeforeAfter falls through to the per-entity flag when the global mode is PER_ENTITY")
	void captureBeforeAfterFallsThroughToEntityFlag() {
		when(entityConfigCache.get("ally")).thenReturn(sampleConfig);

		assertFalse(service.captureBeforeAfter("ally"));
	}

	@Test
	@DisplayName("captureBeforeAfter is forced on globally even if the entity has it disabled")
	void captureBeforeAfterForceEnabledOverridesEntityFlag() {
		when(systemConfigService.getCaptureBeforeAfterMode()).thenReturn(AuditMode.FORCE_ENABLED);

		assertTrue(service.captureBeforeAfter("ally"));
	}

	@Test
	@DisplayName("captureBeforeAfter is forced off globally even if the entity has it enabled")
	void captureBeforeAfterForceDisabledOverridesEntityFlag() {
		sampleConfig.setCaptureBeforeAfter(true);
		when(systemConfigService.getCaptureBeforeAfterMode()).thenReturn(AuditMode.FORCE_DISABLED);

		assertFalse(service.captureBeforeAfter("ally"));
	}

}
