package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.MyPaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
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
import org.springframework.web.bind.annotation.RequestMapping;
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
    @PreAuthorize("hasAuthority('PAYMENT_VIEW_OWN')")
    public ResponseEntity<Page<PaymentDto>> list(
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.listForUser(actor.getUuid(), pageable));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAYMENT_CREATE_OWN')")
    public ResponseEntity<PaymentDto> register(
            @Valid @RequestPart("payment") MyPaymentCreateRequest request,
            @RequestPart(value = "support", required = false) MultipartFile supportFile,
            @AuthenticationPrincipal CustomUserDetails actor) {
        PaymentDto created = paymentsService.registerOwn(actor.getUuid(), request, supportFile);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PAYMENT_DELETE_OWN')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        paymentsService.removeOwn(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }
}
