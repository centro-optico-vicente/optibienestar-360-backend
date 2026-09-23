package com.fenixcore.optibienestar360.modules.organization;

import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationCurrenciesDto;
import com.fenixcore.optibienestar360.modules.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The organization's official/reference currencies (ADR 0015 §4) — deliberately
 * NOT under {@code /v1/admin} and carries no {@code @PreAuthorize}, unlike
 * {@code AdminOrganizationController#getMine} (gated by {@code
 * ORGANIZATION_VIEW}, returning the full row: name, legal name, tax id, logo,
 * social links). Which two currencies the system works in is not sensitive,
 * and every authenticated caller — an affiliate registering their own
 * payment, an ally, a promoter, not just an admin — needs it to resolve "the
 * other currency" before previewing a conversion. Same reasoning as
 * {@code ExchangeRateLookupController}. Spring Security's default
 * {@code anyRequest().authenticated()} rule already covers "must be logged
 * in"; no further gate is needed here.
 */
@RestController
@RequestMapping("/v1/organizations")
@RequiredArgsConstructor
public class OrganizationCurrenciesController {

    private final OrganizationService service;

    @GetMapping("/currencies")
    public OrganizationCurrenciesDto currencies() {
        return service.getCurrencies();
    }
}
