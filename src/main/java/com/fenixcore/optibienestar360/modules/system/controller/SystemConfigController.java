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
    @PreAuthorize("hasAnyAuthority('JOB_VIEW_ALL', 'ROLE_VIEW', 'USER_VIEW_ALL', 'REPORT_REPORT_GENERATE')")
    public ResponseEntity<SystemConfigDto> getSystemConfig() {
        SystemConfig config = systemConfigService.getSystemConfig();
        return ResponseEntity.ok(new SystemConfigDto(
                config.getUuid(),
                systemConfigService.getReportFooter(),
                config.getUpdatedAt()
        ));
    }

    @PutMapping
    @Operation(summary = "Actualiza el pie de página de los reportes del sistema")
    @PreAuthorize("hasAnyAuthority('JOB_UPDATE', 'ROLE_UPDATE', 'USER_UPDATE')")
    public ResponseEntity<SystemConfigDto> updateSystemConfig(@Valid @RequestBody UpdateSystemConfigRequest request) {
        SystemConfig updated = systemConfigService.updateReportFooter(request.reportFooter());
        return ResponseEntity.ok(new SystemConfigDto(
                updated.getUuid(),
                updated.getReportFooter(),
                updated.getUpdatedAt()
        ));
    }
}
