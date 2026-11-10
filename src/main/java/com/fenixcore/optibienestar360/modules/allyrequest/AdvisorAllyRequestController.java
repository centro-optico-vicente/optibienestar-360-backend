package com.fenixcore.optibienestar360.modules.allyrequest;

import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestListItemDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AssistedAllyDraftStartRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.service.AllyRequestService;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Advisor portal, ally capture (hub ADR 0037 §5): the advisor registers a business or professional on its
 * behalf, saves it by steps, resumes it later and sends it to review. The advisor is resolved from the JWT
 * (404 {@code me.promoter.not_found} without an active advisor record); another advisor's draft is a 404.
 * Not blocked by the self-registration switch (it is not self-registration); the onboarding policy applies on
 * submit. One granular permission per action:
 * <ul>
 *   <li>{@code POST /drafts} — starts the capture ({@code ALLY_CREATE_ASSISTED}); answers the new ally's uuid.</li>
 *   <li>{@code GET /drafts} — the caller's own drafts ({@code ALLY_DRAFT_VIEW_ASSISTED}).</li>
 *   <li>The steps reuse the ally profile endpoints while the draft is open: {@code GET/PUT
 *   /v1/me/allies/{uuid}/profile}, {@code /licenses} and {@code /opening-hours}
 *   ({@code ALLY_DRAFT_VIEW_ASSISTED} / {@code ALLY_DRAFT_UPDATE_ASSISTED}).</li>
 *   <li>{@code POST /drafts/{uuid}/submit} — creates the contact person's login, sends the invitation and sends the
 *   request to review ({@code ALLY_DRAFT_SUBMIT_ASSISTED}).</li>
 *   <li>{@code DELETE /drafts/{uuid}} — discards the draft ({@code ALLY_DRAFT_DISCARD_ASSISTED}).</li>
 * </ul>
 * {@code /v1/me/advisor/ally-requests} is the primary path; {@code /v1/advisors/me/...} the secondary one.
 */
@RestController
@RequestMapping({"/v1/me/advisor/ally-requests", "/v1/advisors/me/ally-requests"})
@RequiredArgsConstructor
public class AdvisorAllyRequestController {

    private final AllyRequestService service;
    private final RegistrationDraftService draftService;

    @GetMapping("/drafts")
    @PreAuthorize("hasAuthority('ALLY_DRAFT_VIEW_ASSISTED')")
    public ResponseEntity<Page<AllyRequestListItemDto>> drafts(@AuthenticationPrincipal CustomUserDetails actor,
                                                               @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listAssistedDrafts(actor.getUuid(), pageable));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ALLY_CREATE_ASSISTED')")
    public AllyRequestCreatedDto startDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                            @Valid @RequestBody AssistedAllyDraftStartRequest request) {
        return service.startAssistedDraft(request, actor.getUuid());
    }

    @PostMapping("/drafts/{uuid}/submit")
    @PreAuthorize("hasAuthority('ALLY_DRAFT_SUBMIT_ASSISTED')")
    public ResponseEntity<AllyRequestCreatedDto> submitDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                                             @PathVariable UUID uuid) {
        return ResponseEntity.ok(service.submitAssisted(actor.getUuid(), uuid));
    }

    @DeleteMapping("/drafts/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_DRAFT_DISCARD_ASSISTED')")
    public ResponseEntity<Void> discardDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                             @PathVariable UUID uuid) {
        draftService.discardAdvisorAllyDraft(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }
}
