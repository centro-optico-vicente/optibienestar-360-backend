package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.AssignRoleUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.RoleUserDto;
import com.fenixcore.optibienestar360.modules.auth.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/roles/{roleUuid}/users} — manage which
 * users are assigned a given role, mirroring
 * {@link com.fenixcore.optibienestar360.modules.ally.AdminAllyUsersController}'s
 * conventions.
 *
 * <p>Granular per V79: listing is guarded by {@code ROLE_USER_VIEW_ALL};
 * assigning by {@code ROLE_USER_CREATE}; removing by {@code ROLE_USER_DELETE}.
 * Distinct from {@code ROLE_PERMISSION_EDIT} (which governs the role's own
 * permission set, not its membership).</p>
 */
@RestController
@RequestMapping("/v1/admin/roles/{roleUuid}/users")
@RequiredArgsConstructor
public class AdminRoleUsersController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER_VIEW_ALL')")
    public ResponseEntity<List<RoleUserDto>> list(@PathVariable UUID roleUuid, Pageable pageable) {
        return ResponseEntity.ok(roleService.listUsers(roleUuid, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USER_CREATE')")
    public ResponseEntity<Void> assign(@PathVariable UUID roleUuid,
                                       @Valid @RequestBody AssignRoleUserRequest request) {
        roleService.assignUser(roleUuid, request.userUuid());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userUuid}")
                .buildAndExpand(request.userUuid())
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @DeleteMapping("/{userUuid}")
    @PreAuthorize("hasAuthority('ROLE_USER_DELETE')")
    public ResponseEntity<Void> remove(@PathVariable UUID roleUuid,
                                       @PathVariable UUID userUuid) {
        roleService.removeUser(roleUuid, userUuid);
        return ResponseEntity.noContent().build();
    }
}
