package com.fenixcore.optisaludplus.modules.auth;

import com.fenixcore.optisaludplus.modules.auth.dto.RoleDto;
import com.fenixcore.optisaludplus.modules.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW_ALL')")
    public ResponseEntity<List<RoleDto>> list() {
        return ResponseEntity.ok(roleService.listActiveRoles());
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('USER_VIEW_ALL')")
    public ResponseEntity<RoleDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(roleService.getRole(uuid));
    }
}
