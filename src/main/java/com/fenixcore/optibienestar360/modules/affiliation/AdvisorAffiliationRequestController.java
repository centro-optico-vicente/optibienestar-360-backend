package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListItemDto;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Advisor portal: the affiliation requests that came through the caller's own
 * QR ({@code GET /v1/advisors/me/affiliation-requests}), read-only, gated by
 * {@code AFFILIATION_REQUEST_VIEW_OWN}. The advisor is resolved from the JWT;
 * 404 when the caller has no active advisor record.
 */
@RestController
@RequestMapping("/v1/advisors/me/affiliation-requests")
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
}
