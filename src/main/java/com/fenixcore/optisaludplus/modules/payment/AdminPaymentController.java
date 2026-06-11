package com.fenixcore.optisaludplus.modules.payment;

import com.fenixcore.optisaludplus.modules.payment.dto.PaymentApproveRequest;
import com.fenixcore.optisaludplus.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optisaludplus.modules.payment.dto.PaymentDto;
import com.fenixcore.optisaludplus.modules.payment.dto.PaymentRejectRequest;
import com.fenixcore.optisaludplus.modules.payment.service.PaymentsService;
import com.fenixcore.optisaludplus.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Admin surface for the payments workflow. This commit lands only the
 * registration entry point ({@code POST}) and the single-record read
 * needed to inspect the result; approve / reject / list endpoints land in
 * follow-up bullets.
 *
 * <p>The registration endpoint accepts {@code multipart/form-data} with
 * two parts: a JSON {@code payment} part (the structured metadata) and an
 * optional {@code support} part (the proof-of-payment file). The file
 * part is metadata-only when {@code storage.r2.enabled=false} — the bytes
 * are discarded but the metadata lands so the admin sees that a proof was
 * attached. Once R2 is wired up in a follow-up bullet, the same code path
 * starts persisting the file content without further changes.</p>
 */
@RestController
@RequestMapping("/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentsService paymentsService;

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW_ALL')")
    public ResponseEntity<PaymentDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(paymentsService.get(uuid));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAYMENT_REGISTER')")
    public ResponseEntity<PaymentDto> register(
            @Valid @RequestPart("payment") PaymentCreateRequest request,
            @RequestPart(value = "support", required = false) MultipartFile supportFile) {
        PaymentDto created = paymentsService.register(request, supportFile);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}/approve")
    @PreAuthorize("hasAuthority('PAYMENT_APPROVE')")
    public ResponseEntity<PaymentDto> approve(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) PaymentApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.approve(uuid, request, actor.getUuid()));
    }

    @PutMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('PAYMENT_REJECT')")
    public ResponseEntity<PaymentDto> reject(
            @PathVariable UUID uuid,
            @Valid @RequestBody PaymentRejectRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.reject(uuid, request, actor.getUuid()));
    }
}
