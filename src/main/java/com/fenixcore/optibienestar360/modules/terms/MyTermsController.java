package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.modules.terms.dto.PendingTermDto;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptRequest;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
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

/**
 * Self-service T&C acceptance — no catalog permission, any authenticated
 * user (the endpoints are scoped by JWT subject, same posture as
 * {@code MyAlliesController}). The frontend calls {@code /pending} once per
 * session/hydration and blocks the app behind {@code TermsAcceptanceModal}
 * until {@code /accept} clears it.
 */
@RestController
@RequestMapping("/v1/me/terms")
@RequiredArgsConstructor
public class MyTermsController {

    private final TermsAcceptanceService service;

    @GetMapping("/pending")
    public ResponseEntity<List<PendingTermDto>> pending(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.pendingForUser(actor.getUuid()));
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal CustomUserDetails actor,
                                       @Valid @RequestBody TermsAcceptRequest request) {
        service.accept(actor.getUuid(), request.termsVersionUuids());
        return ResponseEntity.noContent().build();
    }
}
