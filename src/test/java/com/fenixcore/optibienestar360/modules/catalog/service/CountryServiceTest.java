package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.repository.CountryRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.StateRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
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
 * Unit tests for {@link CountryService#create}/{@link CountryService#update}
 * resolving {@code officialCurrencyUuid} (V96, plan "CRUD admin de Currency +
 * ExchangeRate y país↔moneda oficial") — same style as
 * {@code CurrencyConversionServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

    @Mock private CountryRepository repository;
    @Mock private StateRepository stateRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    /**
     * Built per-call, not as a field initializer — {@code @Mock} fields are
     * injected by {@link MockitoExtension} after test-instance construction,
     * so a field initializer here would capture them as {@code null} (same
     * fix as {@code CurrencyConversionServiceTest.serviceWithRepo()}).
     */
    private CountryService service() {
        return new CountryService(repository, stateRepository, currencyRepository, defaultSortResolver);
    }

    private static Currency currency(UUID uuid, String code, String name) {
        Currency c = new Currency();
        c.setUuid(uuid);
        c.setCode(code);
        c.setName(name);
        return c;
    }

    @Test
    void createResolvesOfficialCurrencyWhenProvided() {
        UUID currencyUuid = UUID.randomUUID();
        Currency ves = currency(currencyUuid, "VES", "Bolívar venezolano");
        when(currencyRepository.findByUuid(currencyUuid)).thenReturn(Optional.of(ves));
        when(repository.save(any(Country.class))).thenAnswer(inv -> inv.getArgument(0));

        CountryDto result = service().create(new CountryCreateRequest("VE", "Venezuela", currencyUuid));

        assertThat(result.officialCurrency()).isNotNull();
        assertThat(result.officialCurrency().uuid()).isEqualTo(currencyUuid);
        assertThat(result.officialCurrency().code()).isEqualTo("VES");
    }

    @Test
    void createLeavesOfficialCurrencyNullWhenOmitted() {
        when(repository.save(any(Country.class))).thenAnswer(inv -> inv.getArgument(0));

        CountryDto result = service().create(new CountryCreateRequest("CO", "Colombia", null));

        assertThat(result.officialCurrency()).isNull();
    }

    @Test
    void createThrowsOnAnUnresolvableOfficialCurrencyUuid() {
        UUID bogus = UUID.randomUUID();
        when(currencyRepository.findByUuid(bogus)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(new CountryCreateRequest("XX", "Nowhere", bogus)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("country.official_currency.not_found");
    }

    @Test
    void updateResolvesOfficialCurrencyWhenProvided() {
        UUID uuid = UUID.randomUUID();
        Country existing = new Country();
        existing.setUuid(uuid);
        existing.setIsoCode("VE");
        existing.setName("Venezuela");
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));

        UUID currencyUuid = UUID.randomUUID();
        Currency ves = currency(currencyUuid, "VES", "Bolívar venezolano");
        when(currencyRepository.findByUuid(currencyUuid)).thenReturn(Optional.of(ves));
        when(repository.save(any(Country.class))).thenAnswer(inv -> inv.getArgument(0));

        CountryDto result = service().update(uuid, new CountryUpdateRequest("Venezuela", null, currencyUuid));

        assertThat(result.officialCurrency()).isNotNull();
        assertThat(result.officialCurrency().code()).isEqualTo("VES");
    }

    @Test
    void updateWithNullOfficialCurrencyUuidLeavesExistingValueUnchanged() {
        UUID uuid = UUID.randomUUID();
        Country existing = new Country();
        existing.setUuid(uuid);
        existing.setIsoCode("VE");
        existing.setName("Venezuela");
        Currency ves = currency(UUID.randomUUID(), "VES", "Bolívar venezolano");
        existing.setOfficialCurrency(ves);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(Country.class))).thenAnswer(inv -> inv.getArgument(0));

        CountryDto result = service().update(uuid, new CountryUpdateRequest("Venezuela", null, null));

        assertThat(result.officialCurrency()).isNotNull();
        assertThat(result.officialCurrency().code()).isEqualTo("VES");
    }
}
