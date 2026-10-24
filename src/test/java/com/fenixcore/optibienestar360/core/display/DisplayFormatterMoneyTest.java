package com.fenixcore.optibienestar360.core.display;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DisplayFormatter#money(BigDecimal, String, Locale)} — renders in the
 * row's own ISO currency (hub ADR 0015) and falls back to {@code VES} when the
 * code is missing or unknown, which is the contract the removed
 * {@code money(BigDecimal, Locale)} overload used to provide.
 */
class DisplayFormatterMoneyTest {

    private static final Locale ES_VE = Locale.forLanguageTag("es-VE");
    private static final BigDecimal AMOUNT = new BigDecimal("1200.50");

    private final DisplayFormatter formatter = new DisplayFormatter(new StaticMessageSource());

    private static String expected(BigDecimal value, String currencyCode, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance(currencyCode));
        return format.format(value);
    }

    @Test
    void nullValue_rendersNull() {
        assertThat(formatter.money(null, "USD", ES_VE)).isNull();
    }

    @Test
    void knownCurrency_usesItsOwnSymbol() {
        assertThat(formatter.money(AMOUNT, "USD", ES_VE)).isEqualTo(expected(AMOUNT, "USD", ES_VE));
    }

    @Test
    void nullOrBlankCurrency_fallsBackToVes() {
        String ves = expected(AMOUNT, "VES", ES_VE);
        assertThat(formatter.money(AMOUNT, null, ES_VE)).isEqualTo(ves);
        assertThat(formatter.money(AMOUNT, "  ", ES_VE)).isEqualTo(ves);
    }

    @Test
    void unknownCurrency_fallsBackToVes() {
        assertThat(formatter.money(AMOUNT, "NOT_ISO", ES_VE)).isEqualTo(expected(AMOUNT, "VES", ES_VE));
    }
}
