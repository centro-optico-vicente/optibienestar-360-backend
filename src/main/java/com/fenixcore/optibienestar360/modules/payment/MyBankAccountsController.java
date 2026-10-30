package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountRequest;
import com.fenixcore.optibienestar360.modules.payment.service.PayerBankAccountService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * The caller's own origin bank accounts (V204): list, add, edit and remove. They are the caller's own data, so no
 * permission beyond being logged in is needed — every call is scoped to the caller's person, never to anyone else's.
 * Ticking "remember this account" on a payment ({@code POST /v1/me/payments}) adds to the same list.
 */
@RestController
@RequestMapping("/v1/me/bank-accounts")
@RequiredArgsConstructor
public class MyBankAccountsController {

    private final PayerBankAccountService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PayerBankAccountDto>> list(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.listMine(actor.getUuid()));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayerBankAccountDto> get(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.getMine(actor.getUuid(), uuid));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayerBankAccountDto> create(@Valid @RequestBody PayerBankAccountRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails actor) {
        PayerBankAccountDto created = service.createMine(actor.getUuid(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayerBankAccountDto> update(@PathVariable UUID uuid,
                                                      @Valid @RequestBody PayerBankAccountRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.updateMine(actor.getUuid(), uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails actor) {
        service.deleteMine(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }
}
