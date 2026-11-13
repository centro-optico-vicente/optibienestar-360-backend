package com.fenixcore.optibienestar360.core.config;

import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests for the Redis cache value serializer: what a
 * {@code @Cacheable} method returns must come back as the same types on a
 * cache hit. DTOs are Java records (final classes); typing only non-final
 * classes turned a cached {@code List<CurrencyDto>} into a list of maps, so
 * any caller reading the elements failed with a ClassCastException on every
 * hit (e.g. {@code GET /v1/public/payment-methods} filters
 * {@code PaymentMethodDto::requiresApproval}).
 */
class RedisCacheConfigTest {

    record Sample(UUID uuid, String code, BigDecimal amount, Instant at, LocalDate day, Nested nested) {}

    record Nested(String label, List<String> tags) {}

    private final RedisSerializer<Object> serializer = RedisCacheConfig.cacheValueSerializer();

    private Object roundTrip(Object value) {
        return serializer.deserialize(serializer.serialize(value));
    }

    @Test
    void aCachedListOfRecordDtosComesBackAsThoseDtos() {
        List<CurrencyDto> cached = List.of(
                new CurrencyDto(UUID.randomUUID(), "USD", "Dólar", "US$", (short) 2, true),
                new CurrencyDto(UUID.randomUUID(), "VES", "Bolívar", "Bs.", (short) 2, true));

        Object back = roundTrip(cached);

        assertThat(back).isInstanceOf(List.class);
        assertThat((List<?>) back).allSatisfy(e -> assertThat(e).isInstanceOf(CurrencyDto.class));
        assertThat(back).isEqualTo(cached);
    }

    @Test
    void recordsKeepTheirNestedTypesAndJavaTimeValues() {
        Sample sample = new Sample(UUID.randomUUID(), "X", new BigDecimal("10.50"),
                Instant.parse("2026-11-12T10:15:30Z"), LocalDate.of(2026, 11, 12),
                new Nested("n", List.of("a", "b")));

        Object back = roundTrip(sample);

        assertThat(back).isEqualTo(sample);
    }

    @Test
    void listsFromStreamToListRoundTrip() {
        // Stream.toList() returns a final JDK class (ImmutableCollections.ListN).
        List<String> cached = java.util.stream.Stream.of("a", "b", "c").toList();

        assertThat(roundTrip(cached)).isEqualTo(cached);
    }

    @Test
    void plainValuesStillRoundTrip() {
        assertThat(roundTrip("text")).isEqualTo("text");
        assertThat(roundTrip(Map.of("k", 1))).isEqualTo(Map.of("k", 1));
        assertThat(roundTrip(true)).isEqualTo(true);
    }
}
