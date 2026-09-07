package com.fenixcore.optibienestar360.modules.organization.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationUpdateRequest;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin surface over the single {@code organizations} row (ADR 0015 §4, plan
 * "CRUD admin de Currency + ExchangeRate y país↔moneda oficial"). No
 * list/create/delete — see {@code V98} for why only view/update exist.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository repository;
    private final CurrencyRepository currencyRepository;

    public OrganizationDto getMine() {
        return toDto(repository.findSingleton());
    }

    @Transactional
    @Auditable(entity = "organization", action = AuditAction.UPDATE)
    public OrganizationDto updateMine(OrganizationUpdateRequest req) {
        Organization org = repository.findSingleton();
        org.setName(req.name());
        org.setLegalName(req.legalName());
        org.setTaxIdentifier(req.taxIdentifier());
        org.setLogoKey(req.logoKey());
        if (req.officialCurrencyUuid() != null) {
            org.setOfficialCurrency(resolveCurrency(req.officialCurrencyUuid()));
        }
        if (req.referenceCurrencyUuid() != null) {
            org.setReferenceCurrency(resolveCurrency(req.referenceCurrencyUuid()));
        }
        return toDto(repository.save(org));
    }

    /**
     * Same "unresolvable UUID is a validation error, omitted is a no-op"
     * convention as {@code CountryService.resolveCurrency} — an admin picking
     * from a dropdown should never be able to submit a dangling reference.
     */
    private Currency resolveCurrency(UUID uuid) {
        return currencyRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("organization.currency.not_found"));
    }

    private static OrganizationDto toDto(Organization o) {
        return new OrganizationDto(
                o.getUuid(),
                o.getName(),
                o.getLegalName(),
                o.getTaxIdentifier(),
                o.getLogoKey(),
                DisplayRefs.ref(o.getOfficialCurrency()),
                DisplayRefs.ref(o.getReferenceCurrency()));
    }
}
