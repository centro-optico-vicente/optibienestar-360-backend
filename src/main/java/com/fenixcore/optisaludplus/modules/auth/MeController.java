package com.fenixcore.optisaludplus.modules.auth;

import com.fenixcore.optisaludplus.modules.auth.dto.ChangePasswordRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.UserDto;
import com.fenixcore.optisaludplus.modules.auth.service.AuthService;
import com.fenixcore.optisaludplus.modules.auth.service.UserService;
import com.fenixcore.optisaludplus.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request, principal.getUuid());
        return ResponseEntity.noContent().build();
    }
}
