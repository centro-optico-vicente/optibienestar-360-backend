package com.fenixcore.optibienestar360.modules.system.controller;

import com.fenixcore.optibienestar360.modules.system.dto.SystemConfigDto;
import com.fenixcore.optibienestar360.modules.system.dto.UpdateSystemConfigRequest;
import com.fenixcore.optibienestar360.modules.system.entity.SystemConfig;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/system-configs")
@Tag(name = "Configuración del Sistema", description = "Endpoints para la gestión de parámetros globales del sistema")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    @Operation(summary = "Obtiene la configuración activa del sistema")
    @PreAuthorize("hasAnyAuthority('JOB_VIEW_ALL', 'ROLE_VIEW', 'USER_VIEW_ALL', 'REPORT_REPORT_GENERATE', 'AUDIT_MANAGE_CONFIG')")
    public ResponseEntity<SystemConfigDto> getSystemConfig() {
        SystemConfig config = systemConfigService.getSystemConfig();
        return ResponseEntity.ok(toDto(config));
    }

    @PutMapping
    @Operation(summary = "Actualiza la configuración del sistema (pie de página de reportes, overrides globales de auditoría)")
    @PreAuthorize("hasAnyAuthority('JOB_UPDATE', 'ROLE_UPDATE', 'USER_UPDATE', 'AUDIT_MANAGE_CONFIG')")
    public ResponseEntity<SystemConfigDto> updateSystemConfig(@Valid @RequestBody UpdateSystemConfigRequest request) {
        SystemConfig updated = systemConfigService.updateSystemConfig(request);
        return ResponseEntity.ok(toDto(updated));
    }

    private static SystemConfigDto toDto(SystemConfig config) {
        return new SystemConfigDto(
                config.getUuid(),
                config.getReportFooter(),
                config.getDataChangeAuditMode(),
                config.getReportAuditMode(),
                config.isLoginAuditEnabled(),
				config.getLoginSessionExpirationDays(),
                config.getUpdatedAt()
        );
    }
}
