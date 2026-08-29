package com.fenixcore.optibienestar360.core.audit.controller;

import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import com.fenixcore.optibienestar360.core.audit.dto.CreateEntityConfigRequest;
import com.fenixcore.optibienestar360.core.audit.dto.EntityConfigDto;
import com.fenixcore.optibienestar360.core.audit.dto.UpdateEntityConfigRequest;
import com.fenixcore.optibienestar360.core.audit.entity.EntityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Admin CRUD for {@code entity_config} (V60/V80) — per-entity audit toggles
 * and default sort. Gated by the granular {@code ENTITY_CONFIG_VIEW}/
 * {@code ENTITY_CONFIG_CREATE}/{@code ENTITY_CONFIG_UPDATE}/
 * {@code ENTITY_CONFIG_DELETE} (V81), all deliberately granted to SYSTEM
 * only for now (see that migration's comment). Row creation here is
 * independent from the {@code @Auditable(entity = "...")} annotations
 * elsewhere in the codebase — nothing enforces that every audited entity
 * has a row, or that every row corresponds to a real audited entity; it's
 * an admin-maintained registry, same as the V60 seed data was.
 */
@RestController
@RequestMapping("/v1/admin/entity-config")
@RequiredArgsConstructor
@Tag(name = "Configuración de entidades", description = "Auditoría y orden predeterminado por entidad (solo SYSTEM)")
public class AdminEntityConfigController {

	private final EntityConfigService entityConfigService;

	@GetMapping
	@PreAuthorize("hasAuthority('ENTITY_CONFIG_VIEW')")
	@Operation(summary = "Lista la configuración de todas las entidades registradas")
	public ResponseEntity<List<EntityConfigDto>> list() {
		return ResponseEntity.ok(entityConfigService.listAll().stream().map(AdminEntityConfigController::toDto).toList());
	}

	@GetMapping("/{entityKey}")
	@PreAuthorize("hasAuthority('ENTITY_CONFIG_VIEW')")
	@Operation(summary = "Obtiene la configuración de una entidad")
	public ResponseEntity<EntityConfigDto> get(@PathVariable String entityKey) {
		EntityConfig config = entityConfigService.findConfig(entityKey);
		if (config == null) {
			throw new NoSuchElementException("entity_config.entity_key.not_found");
		}
		return ResponseEntity.ok(toDto(config));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ENTITY_CONFIG_CREATE')")
	@Operation(summary = "Registra la configuración de una entidad nueva (auditoría en true, sin orden predeterminado)")
	public ResponseEntity<EntityConfigDto> create(@Valid @RequestBody CreateEntityConfigRequest request) {
		EntityConfig created = entityConfigService.createConfig(
			request.entityKey(), request.displayName(), request.tableName(), request.notes()
		);
		return ResponseEntity.status(201).body(toDto(created));
	}

	@PutMapping("/{entityKey}")
	@PreAuthorize("hasAuthority('ENTITY_CONFIG_UPDATE')")
	@Operation(summary = "Actualiza la configuración de una entidad (auditoría + orden predeterminado)")
	public ResponseEntity<EntityConfigDto> update(@PathVariable String entityKey,
			@Valid @RequestBody UpdateEntityConfigRequest request) {
		EntityConfig changes = new EntityConfig();
		changes.setEnabled(request.enabled());
		changes.setAuditCreate(request.auditCreate());
		changes.setAuditUpdate(request.auditUpdate());
		changes.setAuditDelete(request.auditDelete());
		changes.setAuditReport(request.auditReport());
		changes.setCaptureBeforeAfter(request.captureBeforeAfter());
		changes.setDefaultSort(request.defaultSort());
		changes.setNotes(request.notes());

		EntityConfig updated = entityConfigService.updateConfig(entityKey, changes);
		return ResponseEntity.ok(toDto(updated));
	}

	@DeleteMapping("/{entityKey}")
	@PreAuthorize("hasAuthority('ENTITY_CONFIG_DELETE')")
	@Operation(summary = "Elimina la configuración de una entidad — no borra ni afecta los datos de esa entidad")
	public ResponseEntity<Void> delete(@PathVariable String entityKey) {
		entityConfigService.deleteConfig(entityKey);
		return ResponseEntity.noContent().build();
	}

	private static EntityConfigDto toDto(EntityConfig config) {
		return new EntityConfigDto(
			config.getUuid(),
			config.getEntityKey(),
			config.getDisplayName(),
			config.getTableName(),
			config.isEnabled(),
			config.isAuditCreate(),
			config.isAuditUpdate(),
			config.isAuditDelete(),
			config.isAuditReport(),
			config.isCaptureBeforeAfter(),
			config.getDefaultSort(),
			config.getNotes(),
			config.getUpdatedAt()
		);
	}

}
