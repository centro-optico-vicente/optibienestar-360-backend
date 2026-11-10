package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationDraftStepRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRegistrationDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListItemDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AssistedAffiliationDraftStartRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AssistedAffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import com.fenixcore.optibienestar360.modules.onboarding.dto.DraftRetentionDto;
import com.fenixcore.optibienestar360.modules.onboarding.dto.DraftRetentionRequest;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Advisor portal, the caller's own affiliation requests. The advisor is
 * resolved from the JWT; 404 when the caller has no active advisor record.
 * <ul>
 *   <li>{@code GET} — the requests that came through the caller's QR or that
 *   they filled themselves, read-only ({@code AFFILIATION_REQUEST_VIEW_OWN}).</li>
 *   <li>{@code POST} — assisted affiliation (V194): the advisor fills the form
 *   for a family on their device and the family accepts the contract there
 *   ({@code AFFILIATION_REQUEST_CREATE_ASSISTED}). Same validations as the
 *   public form; the request is attributed to the caller, the password is
 *   optional.</li>
 *   <li>{@code /drafts} — the same assisted affiliation as a DRAFT saved step by step
 *   and resumed later (hub ADR 0037 §5), one granular permission per action:
 *   {@code POST /drafts} starts it ({@code AFFILIATION_REQUEST_CREATE_ASSISTED}),
 *   {@code GET /drafts} and {@code GET /drafts/{uuid}} list and read the caller's own
 *   ({@code AFFILIATION_REQUEST_VIEW_OWN}), {@code PATCH /drafts/{uuid}/{step}}
 *   ({@code personal | plan | payment | family}) saves a step
 *   ({@code AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED}) and {@code POST /drafts/{uuid}/submit}
 *   sends it to review ({@code AFFILIATION_REQUEST_DRAFT_SUBMIT_ASSISTED}). Another
 *   advisor's draft is a 404; {@code DELETE /drafts/{uuid}} discards the caller's own draft
 *   ({@code AFFILIATION_REQUEST_DRAFT_DISCARD_ASSISTED}). {@code PUT /drafts/{uuid}/retain}
 *   ({@code retain}, optional {@code note}) keeps a draft of the caller's scope out of the abandoned-draft cleanup
 *   or releases it, and {@code GET /in-progress} lists that scope: the caller's own drafts (assisted or from their
 *   QR) and the self-registered ones nobody is attributed to ({@code AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR}). The one-shot {@code POST} above keeps working.</li>
 * </ul>
 * {@code /v1/me/advisor/affiliation-requests} is the primary path;
 * {@code /v1/advisors/me/...} is the secondary path (not deprecated).
 */
@RestController
// Primary /v1/me/advisor/...; /v1/advisors/me/... is the secondary path (not deprecated)
@RequestMapping({"/v1/me/advisor/affiliation-requests", "/v1/advisors/me/affiliation-requests"})
@RequiredArgsConstructor
public class AdvisorAffiliationRequestController {

    private final AffiliationRequestService service;
    private final RegistrationDraftService draftService;

    @GetMapping
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_VIEW_OWN')")
    public ResponseEntity<Page<AffiliationRequestListItemDto>> mine(
            @AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listForAdvisor(actor.getUuid(), pageable, status));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_CREATE_ASSISTED')")
    public AffiliationRequestCreatedDto createAssisted(@AuthenticationPrincipal CustomUserDetails actor,
                                                       @Valid @RequestBody AssistedAffiliationRequestCreateRequest request,
                                                       HttpServletRequest httpRequest) {
        return service.createAssisted(request, actor.getUuid(), ClientRequestMetadata.from(httpRequest));
    }

    // ─── Assisted drafts ────────────────────────────────────────────────────

    @GetMapping("/drafts")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_VIEW_OWN')")
    public ResponseEntity<Page<AffiliationRequestListItemDto>> drafts(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listAssistedDrafts(actor.getUuid(), pageable));
    }

    /** In-progress registrations to keep: the caller's own and the unattributed self-registered ones. */
    @GetMapping("/in-progress")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR')")
    public ResponseEntity<Page<AffiliationRequestListItemDto>> inProgress(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listInProgressForAdvisor(actor.getUuid(), pageable));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_CREATE_ASSISTED')")
    public AffiliationRegistrationDto startDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                                 @Valid @RequestBody AssistedAffiliationDraftStartRequest request,
                                                 HttpServletRequest httpRequest) {
        return service.startAssistedDraft(request, actor.getUuid(), ClientRequestMetadata.from(httpRequest));
    }

    @GetMapping("/drafts/{uuid}")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_VIEW_OWN')")
    public ResponseEntity<AffiliationRegistrationDto> getDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                                               @PathVariable UUID uuid) {
        return ResponseEntity.ok(service.getAssistedDraft(actor.getUuid(), uuid));
    }

    @PatchMapping("/drafts/{uuid}/{step}")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED')")
    public ResponseEntity<AffiliationRegistrationDto> saveDraftStep(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID uuid,
            @PathVariable String step,
            @Valid @RequestBody AffiliationDraftStepRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.saveAssistedStep(actor.getUuid(), uuid, step, request, null,
                ClientRequestMetadata.from(httpRequest)));
    }

    /** The payment step with the proof of payment as the {@code support} part. */
    @PatchMapping(value = "/drafts/{uuid}/{step}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED')")
    public ResponseEntity<AffiliationRegistrationDto> saveDraftStepMultipart(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID uuid,
            @PathVariable String step,
            @Valid @RequestPart("request") AffiliationDraftStepRequest request,
            @RequestPart(value = "support", required = false) MultipartFile support,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.saveAssistedStep(actor.getUuid(), uuid, step, request, support,
                ClientRequestMetadata.from(httpRequest)));
    }

    @PostMapping("/drafts/{uuid}/submit")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_DRAFT_SUBMIT_ASSISTED')")
    public ResponseEntity<AffiliationRegistrationDto> submitDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                                                  @PathVariable UUID uuid) {
        return ResponseEntity.ok(service.submitAssisted(actor.getUuid(), uuid));
    }

    @DeleteMapping("/drafts/{uuid}")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_DRAFT_DISCARD_ASSISTED')")
    public ResponseEntity<Void> discardDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                             @PathVariable UUID uuid) {
        draftService.discardAssistedAffiliationDraft(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/drafts/{uuid}/retain")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR')")
    public ResponseEntity<DraftRetentionDto> retainDraft(@AuthenticationPrincipal CustomUserDetails actor,
                                                         @PathVariable UUID uuid,
                                                         @Valid @RequestBody DraftRetentionRequest request) {
        return ResponseEntity.ok(draftService.retainAdvisorAffiliationDraft(actor.getUuid(), uuid,
                request.retain(), request.note()));
    }
}
