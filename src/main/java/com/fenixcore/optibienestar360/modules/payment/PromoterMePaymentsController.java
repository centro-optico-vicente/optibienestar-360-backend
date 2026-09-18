package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentsService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * <p>Gated on {@code PROMOTER_VIEW_OWN} (V35) — same permission
 * {@code PromoterMeController}/{@code PromoterContactsController} already
 * use for the rest of the promoter self-service surface, rather than adding
 * a new one just for this screen.</p>
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
}
