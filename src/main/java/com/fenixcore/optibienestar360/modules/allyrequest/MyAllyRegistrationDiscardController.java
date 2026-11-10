package com.fenixcore.optibienestar360.modules.allyrequest;

import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Discards the ally registration in progress ({@code DELETE /v1/me/allies/{allyUuid}/registration}, hub
 * ADR 0037 §2): the OWNER of a self-registered ally that was not sent to review yet removes it, which frees
 * the cédula, RIF and email for a new registration. 422 once it was submitted. Gated by
 * {@code ALLY_REGISTRATION_DISCARD_OWN}.
 */
@RestController
@RequestMapping({"/v1/me/allies/{allyUuid}/registration", "/v1/allies/me/{allyUuid}/registration"})
@RequiredArgsConstructor
public class MyAllyRegistrationDiscardController {

    private final RegistrationDraftService draftService;

    @DeleteMapping
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_DISCARD_OWN')")
    public ResponseEntity<Void> discard(@PathVariable UUID allyUuid,
                                        @AuthenticationPrincipal CustomUserDetails actor) {
        draftService.discardOwnAllyRegistration(allyUuid, actor.getUuid());
        return ResponseEntity.noContent().build();
    }
}
