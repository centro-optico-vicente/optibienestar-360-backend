package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestApproveRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestDetailDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListItemDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestRejectRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestUpdateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentSupportUrlDto;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Review of the public affiliation requests (V190). One granular permission
 * per action: {@code AFFILIATION_REQUEST_VIEW_ALL} (list + detail with its
 * log), {@code _UPDATE} (correct the data while PENDING), {@code _APPROVE}
 * (creates the user, the member and the SUSPENDED membership) and
 * {@code _REJECT} (reason required, emailed to the applicant).
 */
@RestController
@RequestMapping("/v1/admin/affiliation-requests")
@RequiredArgsConstructor
public class AdminAffiliationRequestController {

    private final AffiliationRequestService service;

    /** {@code ?status=PENDING|APPROVED|REJECTED} (all when absent), {@code ?q=} free text; newest first by default. */
    @GetMapping
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<AffiliationRequestListItemDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        Page<AffiliationRequestListItemDto> page = service.list(pageable, status, q);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_VIEW_ALL')")
    public ResponseEntity<AffiliationRequestDetailDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    /**
     * Short-lived presigned URL of the proof attached to the inscription
     * payment reported on the form — same contract as
     * {@code GET /v1/admin/payments/{uuid}/support} ({@code ttlMinutes} clamped
     * to [1, 60], default 5). 404 when no proof was stored.
     */
    @GetMapping("/{uuid}/payment-support")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_VIEW_ALL')")
    public ResponseEntity<PaymentSupportUrlDto> getPaymentSupportUrl(
            @PathVariable UUID uuid,
            @RequestParam(value = "ttlMinutes", required = false) @Min(1) @Max(60) Integer ttlMinutes) {
        Duration ttl = ttlMinutes != null ? Duration.ofMinutes(ttlMinutes) : null;
        return ResponseEntity.ok(service.paymentSupportUrl(uuid, ttl));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_UPDATE')")
    public ResponseEntity<AffiliationRequestDetailDto> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody AffiliationRequestUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.update(uuid, request, actor.getUuid()));
    }

    @PostMapping("/{uuid}/approve")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_APPROVE')")
    public ResponseEntity<AffiliationRequestDetailDto> approve(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) AffiliationRequestApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        String comment = request != null ? request.comment() : null;
        return ResponseEntity.ok(service.approve(uuid, comment, actor.getUuid()));
    }

    @PostMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('AFFILIATION_REQUEST_REJECT')")
    public ResponseEntity<AffiliationRequestDetailDto> reject(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) AffiliationRequestRejectRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(service.reject(uuid, reason, actor.getUuid()));
    }
}
