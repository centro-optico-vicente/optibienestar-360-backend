package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyUsersService;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/users} — manage which
 * system users can operate on this ally and with what intra-ally role
 * (OWNER / STAFF / VIEWER) + primary contact flag.
 *
 * <p>Reads are guarded by {@code ALLY_VIEW_ALL}. Writes are guarded by
 * {@code ALLY_USERS_MANAGE} — a dedicated permission distinct from
 * {@code ALLY_UPDATE} (which governs the ally's own business data, not who
 * operates on its behalf).</p>
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/users")
@RequiredArgsConstructor
public class AdminAllyUsersController {

    private final AllyUsersService usersService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<List<AllyUserDto>> list(@PathVariable UUID allyUuid) {
        return ResponseEntity.ok(usersService.listForAlly(allyUuid));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<AllyUserDto> get(@PathVariable UUID allyUuid,
                                           @PathVariable UUID uuid) {
        return ResponseEntity.ok(usersService.get(allyUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_USERS_MANAGE')")
    public ResponseEntity<AllyUserDto> add(@PathVariable UUID allyUuid,
                                           @Valid @RequestBody AllyUserCreateRequest request) {
        AllyUserDto created = usersService.add(allyUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_USERS_MANAGE')")
    public ResponseEntity<AllyUserDto> update(@PathVariable UUID allyUuid,
                                              @PathVariable UUID uuid,
                                              @Valid @RequestBody AllyUserUpdateRequest request) {
        return ResponseEntity.ok(usersService.update(allyUuid, uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_USERS_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable UUID allyUuid,
                                       @PathVariable UUID uuid) {
        usersService.delete(allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
