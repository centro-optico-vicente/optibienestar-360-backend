package com.fenixcore.optisaludplus.modules.auth;

import com.fenixcore.optisaludplus.modules.auth.dto.CreateRoleRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.RoleDto;
import com.fenixcore.optisaludplus.modules.auth.dto.UpdateRolePermissionsRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.UpdateRoleRequest;
import com.fenixcore.optisaludplus.modules.auth.service.RoleService;
import com.fenixcore.optisaludplus.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @GetMapping("/{uuid}/permissions")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")
    public ResponseEntity<Set<UUID>> getPermissions(@PathVariable UUID uuid) {
        return ResponseEntity.ok(roleService.getRolePermissions(uuid));
    }

    @PutMapping("/{uuid}/permissions")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")
    public ResponseEntity<Void> updatePermissions(@PathVariable UUID uuid,
                                                  @Valid @RequestBody UpdateRolePermissionsRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails actor) {
        roleService.updateRolePermissions(uuid, new HashSet<>(request.permissionUuids()), actor.getUuid());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")
    public ResponseEntity<RoleDto> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleDto created = roleService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")
    public ResponseEntity<RoleDto> update(@PathVariable UUID uuid,
                                          @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        roleService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
