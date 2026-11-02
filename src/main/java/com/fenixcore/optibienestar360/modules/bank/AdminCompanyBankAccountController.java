package com.fenixcore.optibienestar360.modules.bank;

import com.fenixcore.optibienestar360.modules.bank.dto.CompanyBankAccountDto;
import com.fenixcore.optibienestar360.modules.bank.dto.CompanyBankAccountRequest;
import com.fenixcore.optibienestar360.modules.bank.service.CompanyBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD over the company's own bank accounts (V201), plus the payer-facing read-only list at
 * {@code GET /v1/me/company-bank-accounts} (any authenticated user — it is what they deposit into).
 */
@RestController
@RequiredArgsConstructor
public class AdminCompanyBankAccountController {

    private static final String VIEW   = "hasAuthority('COMPANY_BANK_ACCOUNT_VIEW')";
    private static final String CREATE = "hasAuthority('COMPANY_BANK_ACCOUNT_CREATE')";
    private static final String UPDATE = "hasAuthority('COMPANY_BANK_ACCOUNT_UPDATE')";
    private static final String DELETE = "hasAuthority('COMPANY_BANK_ACCOUNT_DELETE')";

    private final CompanyBankAccountService service;

    @GetMapping("/v1/admin/company-bank-accounts")
    @PreAuthorize(VIEW)
    public ResponseEntity<Page<CompanyBankAccountDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(service.list(pageable, q, includeInactive));
    }

    @GetMapping("/v1/admin/company-bank-accounts/{uuid}")
    @PreAuthorize(VIEW)
    public ResponseEntity<CompanyBankAccountDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping("/v1/admin/company-bank-accounts")
    @PreAuthorize(CREATE)
    public ResponseEntity<CompanyBankAccountDto> create(@Valid @RequestBody CompanyBankAccountRequest req) {
        CompanyBankAccountDto created = service.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/v1/admin/company-bank-accounts/{uuid}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<CompanyBankAccountDto> update(@PathVariable UUID uuid,
                                                        @Valid @RequestBody CompanyBankAccountRequest req) {
        return ResponseEntity.ok(service.update(uuid, req));
    }

    @DeleteMapping("/v1/admin/company-bank-accounts/{uuid}")
    @PreAuthorize(DELETE)
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/me/company-bank-accounts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CompanyBankAccountDto>> forPayers() {
        return ResponseEntity.ok(service.listActive());
    }
}
