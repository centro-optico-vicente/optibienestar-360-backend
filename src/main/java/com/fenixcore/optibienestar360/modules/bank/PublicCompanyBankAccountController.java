package com.fenixcore.optibienestar360.modules.bank;

import com.fenixcore.optibienestar360.modules.bank.dto.CompanyBankAccountDto;
import com.fenixcore.optibienestar360.modules.bank.service.CompanyBankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Company bank accounts flagged public (V204) — no authentication, for the landing page and anyone who wants to
 * know where to pay. Accounts that are not flagged public never appear here (they are only offered to people
 * registering a payment).
 */
@RestController
@RequestMapping("/v1/public/company-bank-accounts")
@RequiredArgsConstructor
public class PublicCompanyBankAccountController {

    private final CompanyBankAccountService service;

    @GetMapping
    public ResponseEntity<List<CompanyBankAccountDto>> list() {
        return ResponseEntity.ok(service.listPublic());
    }
}
