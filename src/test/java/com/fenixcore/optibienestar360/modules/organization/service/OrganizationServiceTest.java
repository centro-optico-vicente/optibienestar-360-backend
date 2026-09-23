package com.fenixcore.optibienestar360.modules.organization.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationUpdateRequest;
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
                new OrganizationUpdateRequest("Nuevo nombre", "Nuevo nombre legal", "J-99999999-0", "logo.png", null, null, null, null, null));

        assertThat(result.name()).isEqualTo("Nuevo nombre");
        assertThat(result.legalName()).isEqualTo("Nuevo nombre legal");
        assertThat(result.officialCurrency().code()).isEqualTo("VES"); // untouched (null in request)
        assertThat(result.referenceCurrency().code()).isEqualTo("USD"); // untouched (null in request)
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
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, eurUuid, null));

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
                new OrganizationUpdateRequest("OptiBienestar 360", null, null, null, null, null, null, bogus, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organization.currency.not_found");
    }
}
