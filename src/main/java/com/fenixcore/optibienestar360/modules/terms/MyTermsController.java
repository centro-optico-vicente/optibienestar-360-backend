package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.terms.dto.PendingTermDto;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptOneRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptanceHistoryDto;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service T&C acceptance — no catalog permission, any authenticated
 * user (the endpoints are scoped by JWT subject, same posture as
 * {@code MyAlliesController}). The frontend calls {@code /pending} once per
 * session/hydration and blocks the app behind {@code TermsAcceptanceModal}
 * until every mandatory item is accepted — one per step with
 * {@code /{termsVersionUuid}/accept}, or several at once with {@code /accept}.
 * {@code /acceptances} is the user's own
 * history ("Mis contratos"): every contract accepted, with the text as
 * accepted.
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
                                       @Valid @RequestBody TermsAcceptRequest request,
                                       HttpServletRequest httpRequest) {
        service.accept(actor.getUuid(), request.allItems(), ClientRequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{termsVersionUuid}/accept")
    public ResponseEntity<Void> acceptOne(@AuthenticationPrincipal CustomUserDetails actor,
                                          @PathVariable UUID termsVersionUuid,
                                          @RequestBody(required = false) TermsAcceptOneRequest request,
                                          HttpServletRequest httpRequest) {
        service.acceptOne(actor.getUuid(), termsVersionUuid, request == null ? null : request.allyAgreementUuid(),
                ClientRequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/acceptances")
    public ResponseEntity<List<TermsAcceptanceHistoryDto>> acceptances(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.historyForUser(actor.getUuid()));
    }
}
