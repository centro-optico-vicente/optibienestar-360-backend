package com.fenixcore.optibienestar360.modules.onboarding;

import com.fenixcore.optibienestar360.modules.onboarding.dto.RegistrationProgressDto;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationProgressService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /v1/me/registration-progress} (hub ADR 0037 §3): the caller's
 * self-registration progress, for the login notice and the "Completar
 * registro" portal. Any authenticated user; scoped to the JWT subject, and a
 * caller without a self-registration gets {@code kind = null}.
 */
@RestController
@RequestMapping("/v1/me/registration-progress")
@RequiredArgsConstructor
public class RegistrationProgressController {

    private final RegistrationProgressService service;

    @GetMapping
    public ResponseEntity<RegistrationProgressDto> get(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.get(actor.getUuid()));
    }
}
