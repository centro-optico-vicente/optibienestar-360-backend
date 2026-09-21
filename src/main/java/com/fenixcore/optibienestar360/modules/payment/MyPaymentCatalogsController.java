package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.bank.dto.BankDto;
import com.fenixcore.optibienestar360.modules.bank.service.BankService;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyDto;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyService;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodDto;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only self-service exposure of the {@code payment_methods}/{@code banks}
 * catalogs (V115/V116) — any authenticated user (affiliate, promoter, admin)
 * needs these to fill out a collection form (method + conditional bank/account
 * fields, same {@code mandatory*} flags {@code AdminPaymentMethodController}
 * exposes), but {@code PAYMENT_METHOD_VIEW_ALL}/{@code BANK_VIEW_ALL} (V119)
 * are only granted to SYSTEM/ADMINISTRADOR — an ordinary member or promoter
 * filling {@code MyPaymentFormModal}/{@code DownlinePaymentFormModal} would
 * 403 against the admin catalog endpoints. This mirrors the read-only path
 * {@code loadAllForDropdown()} already serves (cached, active-only), just
 * without the admin permission gate — no write operation lives here.
 */
@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
public class MyPaymentCatalogsController {

    private final PaymentMethodService paymentMethodService;
    private final BankService bankService;
    private final CurrencyService currencyService;

    @GetMapping("/payment-methods")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaymentMethodDto>> paymentMethods() {
        return ResponseEntity.ok(paymentMethodService.loadAllForDropdown());
    }

    @GetMapping("/banks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BankDto>> banks() {
        return ResponseEntity.ok(bankService.loadAllForDropdown());
    }

    @GetMapping("/currencies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CurrencyDto>> currencies() {
        return ResponseEntity.ok(currencyService.loadAllForDropdown());
    }
}
