package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.MyPaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentLinesUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentsService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Self-service payments surface for the logged-in affiliate.
 *
 * <p>Mirrors the shape of {@code MyMemberController}: a single endpoint
 * that walks the {@code user → person → member → membership} chain via a
 * single JPQL query in the repository, so the affiliate sees their full
 * payment history without ever needing the admin path's RSQL filtering.</p>
 *
 * <p>Register/delete (PAYMENT_CREATE_OWN/PAYMENT_DELETE_OWN, V121) let the
 * affiliate self-manage their own history — the same PENDING-only guard
 * used by the admin surface. Approve/reject stay exclusively an admin
 * action; there is no self-approval path.</p>
 */
@RestController
@RequestMapping("/v1/me/payments")
@RequiredArgsConstructor
public class MyPaymentsController {

    private final PaymentsService paymentsService;

    @GetMapping
	@PreAuthorize("hasAuthority('COLLECTION_VIEW_OWN')")
    public ResponseEntity<Page<PaymentDto>> list(
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.listForUser(actor.getUuid(), pageable));
    }

    /** {@code ?draft=true} starts the payment at {@code DRAFT} — see {@code AdminPaymentController.register}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('COLLECTION_CREATE_OWN')")
    public ResponseEntity<PaymentDto> register(
            @Valid @RequestPart("payment") MyPaymentCreateRequest request,
            @RequestPart(value = "support", required = false) MultipartFile supportFile,
            @RequestParam(required = false, defaultValue = "false") boolean draft,
            @AuthenticationPrincipal CustomUserDetails actor) {
        PaymentDto created = paymentsService.registerOwn(actor.getUuid(), request, supportFile, draft);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** The caller's credit ("saldo a favor") per currency — they may hold some without any membership (V201). */
    @GetMapping("/credit-balance")
    @PreAuthorize("hasAuthority('COLLECTION_VIEW_OWN')")
    public ResponseEntity<com.fenixcore.optibienestar360.modules.payment.dto.CreditBalanceDto> creditBalance(
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.creditBalanceOf(actor.getUuid()));
    }

    /** Confirms the holder the caller pays for, by exact document or contract number (abbreviated name only). */
    @GetMapping("/membership-lookup")
    @PreAuthorize("hasAuthority('COLLECTION_CREATE_OWN')")
    public ResponseEntity<java.util.List<com.fenixcore.optibienestar360.modules.payment.dto.MembershipLookupDto>> membershipLookup(
            @RequestParam(required = false) String documentNumber,
            @RequestParam(required = false) String contractNumber) {
        return ResponseEntity.ok(paymentsService.lookupMembershipForPayer(documentNumber, contractNumber));
    }

    /** Points the caller's own not-yet-approved payment at a membership (e.g. paying a relative-holder's plan). */
    @PutMapping("/{uuid}/membership")
    @PreAuthorize("hasAuthority('COLLECTION_CREATE_OWN')")
    public ResponseEntity<PaymentDto> assignMembership(
            @PathVariable UUID uuid,
            @Valid @RequestBody com.fenixcore.optibienestar360.modules.payment.dto.OwnPaymentMembershipRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.assignOwnToMembership(actor.getUuid(), uuid, request));
    }

    /** Replaces the lines of the caller's own {@code DRAFT} payment (V117 lines feature). */
    @PutMapping("/{uuid}/lines")
	@PreAuthorize("hasAuthority('COLLECTION_CREATE_OWN')")
    public ResponseEntity<PaymentDto> updateLines(
            @PathVariable UUID uuid,
            @Valid @RequestBody PaymentLinesUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.updateLinesOwn(actor.getUuid(), uuid, request));
    }

    /** {@code DRAFT → PENDING} for the caller's own payment. */
    @PutMapping("/{uuid}/submit")
	@PreAuthorize("hasAuthority('COLLECTION_CREATE_OWN')")
    public ResponseEntity<PaymentDto> submit(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.submitOwn(actor.getUuid(), uuid));
    }

    /** {@code PENDING → DRAFT} for the caller's own payment. */
    @PutMapping("/{uuid}/reactivate")
	@PreAuthorize("hasAuthority('COLLECTION_DELETE_OWN')")
    public ResponseEntity<PaymentDto> reactivate(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.reactivateToDraftOwn(actor.getUuid(), uuid));
    }

    @DeleteMapping("/{uuid}")
	@PreAuthorize("hasAuthority('COLLECTION_DELETE_OWN')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        paymentsService.removeOwn(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }
}
