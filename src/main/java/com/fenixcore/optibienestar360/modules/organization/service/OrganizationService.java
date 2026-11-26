package com.fenixcore.optibienestar360.modules.organization.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationCurrenciesDto;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationUpdateRequest;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationContactDto;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationDto;
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

    /**
     * Backs {@code GET /v1/public/organization}: the identity (nombre comercial,
     * razón social, RIF) only while {@code publicIdentityVisible} is ticked,
     * plus the always-public contact block.
     */
    public PublicOrganizationDto getPublic() {
        Organization org = repository.findSingleton();
        boolean visible = org.isPublicIdentityVisible();
        return new PublicOrganizationDto(
                visible ? org.getName() : null,
                visible ? org.getLegalName() : null,
                visible ? org.getTaxIdentifier() : null,
                toContact(org));
    }

    /** Backs {@code GET /v1/public/organization/contact}. */
    public PublicOrganizationContactDto getPublicContact() {
        return toContact(repository.findSingleton());
    }

    private static PublicOrganizationContactDto toContact(Organization org) {
        return new PublicOrganizationContactDto(
                org.getAddress(),
                org.getEmail(),
                org.getPhones(),
                org.getWhatsapp(),
                org.getInstagram(),
                org.getFacebook());
    }

    /**
     * Backs {@code GET /v1/organizations/currencies} (open to any
     * authenticated caller, no {@code ORGANIZATION_VIEW} required — see
     * {@code OrganizationCurrenciesController}).
     */
    public OrganizationCurrenciesDto getCurrencies() {
        Organization org = repository.findSingleton();
        return new OrganizationCurrenciesDto(
                toCurrencyRef(org.getOfficialCurrency()),
                toCurrencyRef(org.getReferenceCurrency()));
    }

    private static OrganizationCurrenciesDto.CurrencyRef toCurrencyRef(Currency c) {
        return c == null
                ? null
                : new OrganizationCurrenciesDto.CurrencyRef(c.getCode(), c.getSymbol(), c.getDecimalPlaces());
    }

    @Transactional
    @Auditable(entity = "organization", action = AuditAction.UPDATE)
    public OrganizationDto updateMine(OrganizationUpdateRequest req) {
        Organization org = repository.findSingleton();
        org.setName(req.name());
        org.setLegalName(req.legalName());
        org.setTaxIdentifier(req.taxIdentifier());
        org.setLogoKey(req.logoKey());
        org.setWhatsapp(req.whatsapp());
        org.setInstagram(req.instagram());
        org.setFacebook(req.facebook());
        if (req.officialCurrencyUuid() != null) {
            org.setOfficialCurrency(resolveCurrency(req.officialCurrencyUuid()));
        }
        if (req.referenceCurrencyUuid() != null) {
            org.setReferenceCurrency(resolveCurrency(req.referenceCurrencyUuid()));
        }
        org.setAutoApproveCommissions(req.autoApproveCommissions());
        org.setRegistryState(req.registryState());
        org.setSigningCity(req.signingCity());
        org.setServiceArea(req.serviceArea());
        org.setAddress(blankToNull(req.address()));
        org.setEmail(blankToNull(req.email()));
        org.setPhones(blankToNull(req.phones()));
        org.setPublicIdentityVisible(req.publicIdentityVisible());
        applyPublicCounters(org, req);
        applySignatory(org, req);
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
                o.getWhatsapp(),
                o.getInstagram(),
                o.getFacebook(),
                DisplayRefs.ref(o.getOfficialCurrency()),
                DisplayRefs.ref(o.getReferenceCurrency()),
                o.isAutoApproveCommissions(),
                o.getRegistryState(),
                o.getSigningCity(),
                o.getServiceArea(),
                o.getAddress(),
                o.getEmail(),
                o.getPhones(),
                o.getSignatoryName(),
                o.getSignatoryDocumentType(),
                o.getSignatoryDocumentNumber(),
                o.getSignatoryPosition(),
                o.isPublicIdentityVisible(),
                o.isAlliesCounterVisible(),
                o.getAlliesCounterMin(),
                o.getMembersCounterMode(),
                o.getMembersCounterMin());
    }

    /** Public login counters (V265, hub ADR 0042): {@code null} leaves each setting unchanged. */
    private static void applyPublicCounters(Organization org, OrganizationUpdateRequest req) {
        if (req.alliesCounterVisible() != null) {
            org.setAlliesCounterVisible(req.alliesCounterVisible());
        }
        if (req.alliesCounterMin() != null) {
            org.setAlliesCounterMin(req.alliesCounterMin());
        }
        if (req.membersCounterMode() != null) {
            org.setMembersCounterMode(req.membersCounterMode());
        }
        if (req.membersCounterMin() != null) {
            org.setMembersCounterMin(req.membersCounterMin());
        }
    }

    /**
     * Signatory fields: blank → NULL like the contact fields. The document
     * is a pair (V219 CHECK): type and number come together or not at all.
     */
    private static void applySignatory(Organization org, OrganizationUpdateRequest req) {
        String type = blankToNull(req.signatoryDocumentType());
        String number = blankToNull(req.signatoryDocumentNumber());
        if ((type == null) != (number == null)) {
            throw new IllegalArgumentException("organization.signatory_document.incomplete");
        }
        org.setSignatoryName(blankToNull(req.signatoryName()));
        org.setSignatoryDocumentType(type);
        org.setSignatoryDocumentNumber(number);
        org.setSignatoryPosition(blankToNull(req.signatoryPosition()));
    }

    /** Trimmed value; {@code null} when blank, so an emptied field renders its contract pending note. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
