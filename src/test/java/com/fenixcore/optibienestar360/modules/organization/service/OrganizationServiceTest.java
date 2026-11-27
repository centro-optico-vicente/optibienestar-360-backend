package com.fenixcore.optibienestar360.modules.organization.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationUpdateRequest;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationContactDto;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.entity.AlliesCounterMode;
import com.fenixcore.optibienestar360.modules.organization.entity.MembersCounterMode;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrganizationService} (ADR 0015 §4, plan "CRUD admin
 * de Currency + ExchangeRate y país↔moneda oficial") — read/update of the
 * single {@code organizations} row, resolving the two currency FKs the same
 * "omitted = no-op, unresolvable = error" way as {@code CountryService}.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository repository;
    @Mock private CurrencyRepository currencyRepository;

    /**
     * Built per-call, not as a field initializer — {@code @Mock} fields are
     * injected by {@link MockitoExtension} after test-instance construction,
     * so a field initializer here would capture them as {@code null} (same
     * fix as {@code CurrencyConversionServiceTest.serviceWithRepo()}).
     */
    private OrganizationService service() {
        return new OrganizationService(repository, currencyRepository);
    }

    private static Currency currency(UUID uuid, String code, String name) {
        Currency c = new Currency();
        c.setUuid(uuid);
        c.setCode(code);
        c.setName(name);
        return c;
    }

    private static Organization organization(Currency official, Currency reference) {
        Organization o = new Organization();
        o.setUuid(UUID.randomUUID());
        o.setName("OptiBienestar 360");
        o.setLegalName("Centro Óptico Vicente");
        o.setTaxIdentifier("J-12345678-9");
        o.setOfficialCurrency(official);
        o.setReferenceCurrency(reference);
        return o;
    }

    @Test
    void getMineReturnsTheSingletonRow() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        when(repository.findSingleton()).thenReturn(organization(ves, usd));

        OrganizationDto result = service().getMine();

        assertThat(result.name()).isEqualTo("OptiBienestar 360");
        assertThat(result.officialCurrency().code()).isEqualTo("VES");
        assertThat(result.referenceCurrency().code()).isEqualTo("USD");
    }

    @Test
    void updateMineAppliesFieldsAndLeavesCurrenciesUnchangedWhenOmitted() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        Organization existing = organization(ves, usd);
        when(repository.findSingleton()).thenReturn(existing);
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDto result = service().updateMine(
                new OrganizationUpdateRequest("Nuevo nombre", "Nuevo nombre legal", "J-99999999-0", "logo.png", null, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null));

        assertThat(result.name()).isEqualTo("Nuevo nombre");
        assertThat(result.legalName()).isEqualTo("Nuevo nombre legal");
        assertThat(result.officialCurrency().code()).isEqualTo("VES"); // untouched (null in request)
        assertThat(result.referenceCurrency().code()).isEqualTo("USD"); // untouched (null in request)
        assertThat(result.autoApproveCommissions()).isFalse();
    }

    @Test
    void updateMineSetsTheSignatoryTrimmedAndBlankAsNull() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        when(repository.findSingleton()).thenReturn(organization(ves, usd));
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDto filled = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, false,
                        null, null, null, null, null, null, " Ana Gómez ", "V", " 12345678 ", " Gerente general ", false, null, null, null, null));

        assertThat(filled.signatoryName()).isEqualTo("Ana Gómez");
        assertThat(filled.signatoryDocumentType()).isEqualTo("V");
        assertThat(filled.signatoryDocumentNumber()).isEqualTo("12345678");
        assertThat(filled.signatoryPosition()).isEqualTo("Gerente general");

        OrganizationDto cleared = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, false,
                        null, null, null, null, null, null, "  ", " ", "", " ", false, null, null, null, null));

        assertThat(cleared.signatoryName()).isNull();
        assertThat(cleared.signatoryDocumentType()).isNull();
        assertThat(cleared.signatoryDocumentNumber()).isNull();
        assertThat(cleared.signatoryPosition()).isNull();
    }

    @Test
    void updateMineRejectsASignatoryDocumentWithoutItsPair() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        when(repository.findSingleton()).thenReturn(organization(ves, usd));

        assertThatThrownBy(() -> service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, false,
                        null, null, null, null, null, null, null, "V", null, null, false, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organization.signatory_document.incomplete");
    }

    @Test
    void updateMineSetsTheInstitutionalContactTrimmedAndBlankAsNull() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        when(repository.findSingleton()).thenReturn(organization(ves, usd));
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDto result = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, false,
                        null, null, null, " Av. 30 con Calle 21, Acarigua ", "planoptibienestar360@gmail.com", "  ",
                        null, null, null, null, false, null, null, null, null));

        assertThat(result.address()).isEqualTo("Av. 30 con Calle 21, Acarigua");
        assertThat(result.email()).isEqualTo("planoptibienestar360@gmail.com");
        assertThat(result.phones()).isNull();
    }

    @Test
    void updateMineSetsAutoApproveCommissions() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        when(repository.findSingleton()).thenReturn(organization(ves, usd));
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDto result = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, true, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null));

        assertThat(result.autoApproveCommissions()).isTrue();
    }

    @Test
    void updateMineResolvesCurrenciesWhenProvided() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        Organization existing = organization(ves, usd);
        when(repository.findSingleton()).thenReturn(existing);
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID eurUuid = UUID.randomUUID();
        Currency eur = currency(eurUuid, "EUR", "Euro");
        when(currencyRepository.findByUuid(eurUuid)).thenReturn(Optional.of(eur));

        OrganizationDto result = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, eurUuid, null, false, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null));

        assertThat(result.officialCurrency().code()).isEqualTo("EUR");
        assertThat(result.referenceCurrency().code()).isEqualTo("USD"); // untouched
    }

    @Test
    void updateMineThrowsOnAnUnresolvableCurrencyUuid() {
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        Currency usd = currency(UUID.randomUUID(), "USD", "Dólar estadounidense");
        when(repository.findSingleton()).thenReturn(organization(ves, usd));

        UUID bogus = UUID.randomUUID();
        when(currencyRepository.findByUuid(bogus)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, bogus, null, false, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organization.currency.not_found");
    }

    private static Organization publicOrganization(boolean identityVisible) {
        Organization org = organization(null, null);
        org.setAddress("Av. Principal, Caracas");
        org.setEmail("info@example.com");
        org.setPhones("0212-5551234");
        org.setWhatsapp("0412-2925608");
        org.setInstagram("@optibienestar");
        org.setFacebook("optibienestar");
        org.setSignatoryName("Private Signatory");
        org.setPublicIdentityVisible(identityVisible);
        return org;
    }

    @Test
    void getPublicIncludesIdentityWhenTheCheckIsOn() {
        when(repository.findSingleton()).thenReturn(publicOrganization(true));

        PublicOrganizationDto result = service().getPublic();

        assertThat(result.name()).isEqualTo("OptiBienestar 360");
        assertThat(result.legalName()).isEqualTo("Centro Óptico Vicente");
        assertThat(result.taxIdentifier()).isEqualTo("J-12345678-9");
        assertThat(result.contact().whatsapp()).isEqualTo("0412-2925608");
    }

    @Test
    void getPublicHidesIdentityButKeepsContactWhenTheCheckIsOff() {
        when(repository.findSingleton()).thenReturn(publicOrganization(false));

        PublicOrganizationDto result = service().getPublic();

        assertThat(result.name()).isNull();
        assertThat(result.legalName()).isNull();
        assertThat(result.taxIdentifier()).isNull();
        assertThat(result.contact().address()).isEqualTo("Av. Principal, Caracas");
        assertThat(result.contact().email()).isEqualTo("info@example.com");
        assertThat(result.contact().phones()).isEqualTo("0212-5551234");
        assertThat(result.contact().instagram()).isEqualTo("@optibienestar");
        assertThat(result.contact().facebook()).isEqualTo("optibienestar");
    }

    @Test
    void getPublicContactReturnsOnlyContactData() {
        when(repository.findSingleton()).thenReturn(publicOrganization(false));

        PublicOrganizationContactDto result = service().getPublicContact();

        assertThat(result.whatsapp()).isEqualTo("0412-2925608");
        assertThat(result.phones()).isEqualTo("0212-5551234");
        assertThat(result.address()).isEqualTo("Av. Principal, Caracas");
    }

    @Test
    void updateMineStoresThePublicIdentityFlag() {
        when(repository.findSingleton()).thenReturn(organization(null, null));
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDto result = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null, true, null, null, null, null));

        assertThat(result.publicIdentityVisible()).isTrue();
    }

    @Test
    void updateMineStoresThePublicLoginCounters() {
        when(repository.findSingleton()).thenReturn(organization(null, null));
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDto result = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null, false,
                        AlliesCounterMode.PROFESSIONAL, 5, MembersCounterMode.MEMBERS_AND_BENEFICIARIES, 50));

        assertThat(result.alliesCounterMode()).isEqualTo(AlliesCounterMode.PROFESSIONAL);
        assertThat(result.alliesCounterMin()).isEqualTo(5);
        assertThat(result.membersCounterMode()).isEqualTo(MembersCounterMode.MEMBERS_AND_BENEFICIARIES);
        assertThat(result.membersCounterMin()).isEqualTo(50);
    }

    @Test
    void updateMineKeepsThePublicLoginCountersWhenOmitted() {
        Organization existing = organization(null, null);
        existing.setAlliesCounterMode(AlliesCounterMode.HIDDEN);
        existing.setAlliesCounterMin(7);
        existing.setMembersCounterMode(MembersCounterMode.MEMBERS);
        existing.setMembersCounterMin(30);
        when(repository.findSingleton()).thenReturn(existing);
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDto result = service().updateMine(
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null));

        assertThat(result.alliesCounterMode()).isEqualTo(AlliesCounterMode.HIDDEN);
        assertThat(result.alliesCounterMin()).isEqualTo(7);
        assertThat(result.membersCounterMode()).isEqualTo(MembersCounterMode.MEMBERS);
        assertThat(result.membersCounterMin()).isEqualTo(30);
    }

    @Test
    void newOrganizationDefaultsShowAlliesAndMembersWithBeneficiaries() {
        Organization org = new Organization();

        assertThat(org.getAlliesCounterMode()).isEqualTo(AlliesCounterMode.COMMERCIAL_AND_PROFESSIONAL);
        assertThat(org.getAlliesCounterMin()).isEqualTo(20);
        assertThat(org.getMembersCounterMode()).isEqualTo(MembersCounterMode.MEMBERS_AND_BENEFICIARIES);
        assertThat(org.getMembersCounterMin()).isEqualTo(20);
    }
}
