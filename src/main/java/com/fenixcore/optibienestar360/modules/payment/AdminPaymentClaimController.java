package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimResolveRequest;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentCreditClaimService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.UUID;

/**
 * Staff side of the claims on payment credit (V205): list, open one for somebody else, and resolve — keep the
 * amount as credit ("monedero"), refund it (books a reversal; the transfer is finance's) or reject the claim.
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminPaymentClaimController {

    private final PaymentCreditClaimService service;

    @GetMapping("/payment-claims")
    @PreAuthorize("hasAuthority('COLLECTION_CLAIM_VIEW_ALL')")
    public ResponseEntity<Page<PaymentClaimDto>> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.list(status, pageable));
    }

    @PostMapping("/payments/{uuid}/claim")
    @PreAuthorize("hasAuthority('COLLECTION_CLAIM_CREATE')")
    public ResponseEntity<PaymentClaimDto> open(@PathVariable UUID uuid,
                                                @Valid @RequestBody PaymentClaimRequest request,
                                                @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.openFor(actor.getUuid(), uuid, request));
    }

    @PutMapping("/payment-claims/{uuid}/resolve")
    @PreAuthorize("hasAuthority('COLLECTION_CLAIM_RESOLVE')")
    public ResponseEntity<PaymentClaimDto> resolve(@PathVariable UUID uuid,
                                                   @Valid @RequestBody PaymentClaimResolveRequest request,
                                                   @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.resolve(uuid, request, actor.getUuid()));
    }
}
