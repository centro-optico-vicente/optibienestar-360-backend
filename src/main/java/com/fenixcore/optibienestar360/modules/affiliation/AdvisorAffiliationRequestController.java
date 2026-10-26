package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListItemDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AssistedAffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
