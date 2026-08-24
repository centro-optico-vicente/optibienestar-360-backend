package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.AssignRoleUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.RoleUserDto;
import com.fenixcore.optibienestar360.modules.auth.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * <p>Listing is guarded by {@code USER_VIEW_ALL} (viewing users, regardless of
 * which role they hold); assigning/removing is guarded by the dedicated
 * {@code ROLE_USERS_MANAGE} permission — distinct from
 * {@code ROLE_PERMISSION_EDIT} (which governs the role's own permission set,
 * not its membership).</p>
 */
@RestController
@RequestMapping("/v1/admin/roles/{roleUuid}/users")
@RequiredArgsConstructor
public class AdminRoleUsersController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW_ALL')")
    public ResponseEntity<List<RoleUserDto>> list(@PathVariable UUID roleUuid) {
        return ResponseEntity.ok(roleService.listUsers(roleUuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USERS_MANAGE')")
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
    @PreAuthorize("hasAuthority('ROLE_USERS_MANAGE')")
    public ResponseEntity<Void> remove(@PathVariable UUID roleUuid,
                                       @PathVariable UUID userUuid) {
        roleService.removeUser(roleUuid, userUuid);
        return ResponseEntity.noContent().build();
    }
}
