package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyCreateRequest;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyDto;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyUpdateRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CurrencyService} (ADR 0015, plan "CRUD admin de
 * Currency + ExchangeRate") — create/update/soft-delete and the immutable
 * {@code code} natural key, same style as {@code CurrencyConversionServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock private CurrencyRepository repository;
    @Mock private DefaultSortResolver defaultSortResolver;

    /**
     * Built per-call, not as a field initializer — {@code @Mock} fields are
     * injected by {@link MockitoExtension} after test-instance construction,
     * so a field initializer here would capture them as {@code null} (same
     * fix as {@code CurrencyConversionServiceTest.serviceWithRepo()}).
     */
    private CurrencyService service() {
        return new CurrencyService(repository, defaultSortResolver);
    }

    private static Currency currency(UUID uuid, String code, String name, String symbol, int decimalPlaces, boolean active) {
        Currency c = new Currency();
        c.setUuid(uuid);
        c.setCode(code);
        c.setName(name);
        c.setSymbol(symbol);
        c.setDecimalPlaces((short) decimalPlaces);
        c.setActive(active);
        return c;
    }

    @Test
    void createPersistsEveryField() {
        when(repository.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyDto result = service().create(new CurrencyCreateRequest("USDT", "Tether", "USDT", (short) 2));

        assertThat(result.code()).isEqualTo("USDT");
        assertThat(result.name()).isEqualTo("Tether");
        assertThat(result.symbol()).isEqualTo("USDT");
        assertThat(result.decimalPlaces()).isEqualTo((short) 2);
        assertThat(result.active()).isTrue();
    }

    @Test
    void updateChangesNameSymbolDecimalPlacesAndActiveButNeverCode() {
        UUID uuid = UUID.randomUUID();
        Currency existing = currency(uuid, "USD", "Dólar", "US$", 2, true);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyDto result = service().update(uuid,
                new CurrencyUpdateRequest("Dólar estadounidense", "$", (short) 0, false));

        assertThat(result.code()).isEqualTo("USD"); // unchanged — immutable natural key
        assertThat(result.name()).isEqualTo("Dólar estadounidense");
        assertThat(result.symbol()).isEqualTo("$");
        assertThat(result.decimalPlaces()).isEqualTo((short) 0);
        assertThat(result.active()).isFalse();
    }

    @Test
    void updateWithNullDecimalPlacesAndActiveLeavesThemUnchanged() {
        UUID uuid = UUID.randomUUID();
        Currency existing = currency(uuid, "EUR", "Euro", "€", 2, true);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyDto result = service().update(uuid, new CurrencyUpdateRequest("Euro", "€", null, null));

        assertThat(result.decimalPlaces()).isEqualTo((short) 2);
        assertThat(result.active()).isTrue();
    }

    @Test
    void deleteIsSoftOnly() {
        UUID uuid = UUID.randomUUID();
        Currency existing = currency(uuid, "VES", "Bolívar", "Bs.", 2, true);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        service().delete(uuid);

        assertThat(existing.isActive()).isFalse();
        verify(repository, never()).delete(any(Currency.class));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void getThrowsWhenNotFound() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(uuid))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
