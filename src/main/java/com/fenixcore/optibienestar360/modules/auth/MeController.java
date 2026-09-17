package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.AccessTokenResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.ChangePasswordRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.LocalePreferenceRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.MyRoleDto;
import com.fenixcore.optibienestar360.modules.auth.dto.SwitchActiveRoleRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userService.getMe(principal.getUuid()));
    }

    /** Roles the caller may pick as their active session role — powers the role-switch selector. */
    @GetMapping("/roles")
    public ResponseEntity<List<MyRoleDto>> myRoles(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userService.getMyEffectiveRoles(principal.getUuid()));
    }

    /**
     * Switches the caller's active role: closes the current session and opens
     * a new one (fresh access + refresh tokens) scoped to {@code roleUuid},
     * exactly like {@code AuthService.switchActiveRole} — no password
     * re-entry, since the caller is already authenticated.
     */
    @PostMapping("/active-role")
    public ResponseEntity<LoginResponse> switchActiveRole(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody SwitchActiveRoleRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                authService.switchActiveRole(principal.getUuid(), request.roleUuid(), principal.getJti(),
                        principal.getSessionId(), request.refreshToken(), httpRequest)
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request, principal.getUuid(), principal.getJti());
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the caller's locale preference and returns a fresh access token
     * with the new locale claim. The refresh token is unchanged — clients
     * replace only the access token. The previous access token is blacklisted
     * server-side so it cannot continue to drive a stale claim.
     */
    @PostMapping("/locale")
    public ResponseEntity<AccessTokenResponse> updateLocale(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody LocalePreferenceRequest request) {
        return ResponseEntity.ok(
                authService.updateMyLocale(principal.getUuid(), request.locale(), principal.getJti(), principal.getSessionId())
        );
    }
}
