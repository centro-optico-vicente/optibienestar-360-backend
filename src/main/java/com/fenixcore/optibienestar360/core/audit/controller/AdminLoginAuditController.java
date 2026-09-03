package com.fenixcore.optibienestar360.core.audit.controller;

import com.fenixcore.optibienestar360.core.audit.LoginAuditQueryService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditResult;
import com.fenixcore.optibienestar360.core.audit.dto.LoginAuditLogDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin read side of the login/session audit trail (spec 16-audit.md
 * §Login) — every login attempt (success and failure) plus session
 * lifecycle. Insert-only and system-populated from {@code AuthService}.
 */
@RestController
@RequestMapping("/v1/admin/audit/logins")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Consulta de la bitácora de accesos y sesiones")
public class AdminLoginAuditController {

	private final LoginAuditQueryService loginAuditQueryService;

	@GetMapping
	@PreAuthorize("hasAuthority('AUDIT_VIEW_LOGIN')")
	@Operation(summary = "Lista la bitácora de intentos de acceso y sesiones, con filtros por usuario, resultado y fecha")
	public ResponseEntity<Page<LoginAuditLogDto>> list(
			@PageableDefault(size = 20) Pageable pageable,
			@RequestParam(required = false) String email,
			@RequestParam(required = false) UUID userUuid,
			@RequestParam(required = false) LoginAuditResult result,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) String filter) {
		return ResponseEntity.ok(loginAuditQueryService.list(pageable, email, userUuid, result, from, to, filter));
	}

}
