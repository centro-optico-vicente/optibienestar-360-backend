package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.DownlinePaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentApproveRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentRejectRequest;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentsService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
 * Self-service payments surface for the logged-in promoter (hub plan
 * payments-unification, "Mis portales"). Same {@code payments.promoter_id}
 * (denormalized, V117) covers both directions this surface exposes:
 *
 * <ul>
 *   <li>{@code direction=IN} — "Cobros de mis afiliados": collections from
 *       the affiliates in the promoter's downline.</li>
 *   <li>{@code direction=OUT} — "Mis pagos de comisiones": commission
 *       payouts disbursed to the promoter ({@code CommissionPayoutService}).</li>
 * </ul>
 *
 * <p>Gated on {@code PROMOTER_VIEW_OWN} (V35) for the read — same permission
 * {@code PromoterMeController}/{@code PromoterContactsController} already
 * use for the rest of the promoter self-service surface, rather than adding
 * a new one just for this screen.</p>
 *
 * <p>Register/delete/approve/reject (V121) are IN-only (collections) and
 * gated by their own granular permissions ({@code PAYMENT_CREATE_DOWNLINE},
 * {@code PAYMENT_DELETE_DOWNLINE}, {@code PAYMENT_APPROVE_DOWNLINE},
 * {@code PAYMENT_REJECT_DOWNLINE}) — approve/reject exist so an admin can
 * grant them later, but are NOT granted to PROMOTOR by default (see V121
 * header comment): today only staff with bank access does the manual
 * review. The service layer verifies the target member/payment belongs to
 * the caller's own downline on every write.</p>
 */
@RestController
@RequestMapping("/v1/promoter/me/payments")
@RequiredArgsConstructor
@Validated
public class PromoterMePaymentsController {

    private final PaymentsService paymentsService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_OWN')")
    public ResponseEntity<Page<PaymentDto>> list(
            @RequestParam @Pattern(regexp = "^(IN|OUT)$", message = "{payment.direction.invalid}") String direction,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.listForPromoter(actor.getUuid(), direction, pageable));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAYMENT_CREATE_DOWNLINE')")
    public ResponseEntity<PaymentDto> register(
            @Valid @RequestPart("payment") DownlinePaymentCreateRequest request,
            @RequestPart(value = "support", required = false) MultipartFile supportFile,
            @AuthenticationPrincipal CustomUserDetails actor) {
        PaymentDto created = paymentsService.registerForDownline(actor.getUuid(), request, supportFile);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}/approve")
    @PreAuthorize("hasAuthority('PAYMENT_APPROVE_DOWNLINE')")
    public ResponseEntity<PaymentDto> approve(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) PaymentApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.approveForDownline(actor.getUuid(), uuid, request));
    }

    @PutMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('PAYMENT_REJECT_DOWNLINE')")
    public ResponseEntity<PaymentDto> reject(
            @PathVariable UUID uuid,
            @Valid @RequestBody PaymentRejectRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.rejectForDownline(actor.getUuid(), uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PAYMENT_DELETE_DOWNLINE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        paymentsService.removeForDownline(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }
}
