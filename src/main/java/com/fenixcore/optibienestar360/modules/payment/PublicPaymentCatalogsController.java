package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.bank.dto.BankDto;
import com.fenixcore.optibienestar360.modules.bank.service.BankService;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodDto;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Anonymous read-only exposure of the {@code payment_methods}/{@code banks}
 * catalogs for the public affiliation form, which may report the payment of
 * the plan's inscription fee ({@code POST /v1/public/affiliation-requests}).
 * Same cached, active-only lists and JSON shape as
 * {@link MyPaymentCatalogsController}. Under {@code /v1/public/**}, already
 * {@code permitAll()}.
 */
@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class PublicPaymentCatalogsController {

    private final PaymentMethodService paymentMethodService;
    private final BankService bankService;

    /**
     * Only methods that go through review: a direct-receipt one (cash) would be
     * auto-approved, which an anonymous applicant must never trigger — the
     * affiliation request rejects it too.
     */
    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodDto>> paymentMethods() {
        return ResponseEntity.ok(paymentMethodService.loadAllForDropdown().stream()
                .filter(PaymentMethodDto::requiresApproval)
                .toList());
    }

    @GetMapping("/banks")
    public ResponseEntity<List<BankDto>> banks() {
        return ResponseEntity.ok(bankService.loadAllForDropdown());
    }
}
