package com.fenixcore.optisaludplus.modules.payment;

import com.fenixcore.optisaludplus.modules.payment.dto.PaymentDto;
import com.fenixcore.optisaludplus.modules.payment.service.PaymentsService;
import com.fenixcore.optisaludplus.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service payments surface for the logged-in affiliate.
 *
 * <p>Mirrors the shape of {@code MyMemberController}: a single endpoint
 * that walks the {@code user → person → member → membership} chain via a
 * single JPQL query in the repository, so the affiliate sees their full
 * payment history without ever needing the admin path's RSQL filtering.</p>
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
}
