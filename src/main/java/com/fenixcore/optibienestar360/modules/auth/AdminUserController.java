package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.AdminUpdateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW_ALL')")
    public ResponseEntity<Page<UserDto>> list(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(userService.listUsers(filter, q, pageable, actor.getUuid()));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('USER_VIEW_ALL')")
    public ResponseEntity<UserDto> get(@PathVariable UUID uuid,
                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(userService.getUser(uuid, actor.getUuid()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<UserDto> create(@Valid @RequestBody AdminCreateUserRequest request,
                                          @AuthenticationPrincipal CustomUserDetails actor) {
        UserDto created = userService.createUser(request, actor.getUuid());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserDto> update(@PathVariable UUID uuid,
                                          @Valid @RequestBody AdminUpdateUserRequest request,
                                          @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(userService.updateUser(uuid, request, actor.getUuid()));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                       @AuthenticationPrincipal CustomUserDetails actor) {
        userService.deleteUser(uuid, actor.getUuid());
        return ResponseEntity.noContent().build();
    }
}
