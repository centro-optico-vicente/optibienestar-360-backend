package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountDto;
import com.fenixcore.optibienestar360.modules.payment.service.PayerBankAccountService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The origin accounts the caller chose to remember (V204). They are saved by ticking "remember this account"
 * on a payment ({@code POST /v1/me/payments}); here the caller lists them to reuse and removes the ones they no longer want.
 */
@RestController
@RequestMapping("/v1/me/bank-accounts")
@RequiredArgsConstructor
public class MyBankAccountsController {

    private final PayerBankAccountService service;

    @GetMapping
    @PreAuthorize("hasAuthority('COLLECTION_CREATE_OWN')")
    public ResponseEntity<List<PayerBankAccountDto>> list(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.listMine(actor.getUuid()));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_CREATE_OWN')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        service.deleteMine(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }
}
