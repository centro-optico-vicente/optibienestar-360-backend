package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationDraftStepRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRegistrationDto;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The caller's own affiliation registration, completed step by step after the
 * public step 1 (hub ADR 0037 §2). Everything is scoped to the JWT subject:
 * there is no way to reach another person's registration.
 * <ul>
 *   <li>{@code GET} — what is saved so far ({@code AFFILIATION_REGISTRATION_VIEW_OWN}).</li>
 *   <li>{@code PATCH /{step}} — {@code personal | plan | payment | family}
 *   ({@code AFFILIATION_REGISTRATION_UPDATE_OWN}); the payment step also accepts
 *   {@code multipart/form-data} with the proof as the {@code support} part.</li>
 *   <li>{@code POST /submit} — sends it to review and applies the onboarding
 *   policy ({@code AFFILIATION_REGISTRATION_SUBMIT_OWN}).</li>
 *   <li>{@code DELETE} — discards the DRAFT and frees the cédula and email for a new registration
 *   ({@code AFFILIATION_REGISTRATION_DISCARD_OWN}); 422 once it was sent to review. The caller's
 *   login is closed, so the portal signs out afterwards.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/me/affiliation-request")
@RequiredArgsConstructor
public class MyAffiliationRequestController {

    private final AffiliationRequestService service;
    private final RegistrationDraftService draftService;

    @GetMapping
    @PreAuthorize("hasAuthority('AFFILIATION_REGISTRATION_VIEW_OWN')")
    public ResponseEntity<AffiliationRegistrationDto> get(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.getMine(actor.getUuid()));
    }

    @PatchMapping("/{step}")
    @PreAuthorize("hasAuthority('AFFILIATION_REGISTRATION_UPDATE_OWN')")
    public ResponseEntity<AffiliationRegistrationDto> saveStep(
            @PathVariable String step,
            @Valid @RequestBody AffiliationDraftStepRequest request,
            @AuthenticationPrincipal CustomUserDetails actor,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.saveStep(actor.getUuid(), step, request, null,
                ClientRequestMetadata.from(httpRequest)));
    }

    @PatchMapping(value = "/{step}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AFFILIATION_REGISTRATION_UPDATE_OWN')")
    public ResponseEntity<AffiliationRegistrationDto> saveStepMultipart(
            @PathVariable String step,
            @Valid @RequestPart("request") AffiliationDraftStepRequest request,
            @RequestPart(value = "support", required = false) MultipartFile support,
            @AuthenticationPrincipal CustomUserDetails actor,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.saveStep(actor.getUuid(), step, request, support,
                ClientRequestMetadata.from(httpRequest)));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('AFFILIATION_REGISTRATION_SUBMIT_OWN')")
    public ResponseEntity<AffiliationRegistrationDto> submit(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.submit(actor.getUuid()));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('AFFILIATION_REGISTRATION_DISCARD_OWN')")
    public ResponseEntity<Void> discard(@AuthenticationPrincipal CustomUserDetails actor) {
        draftService.discardOwnMemberDraft(actor.getUuid());
        return ResponseEntity.noContent().build();
    }
}
