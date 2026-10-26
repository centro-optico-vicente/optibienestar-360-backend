package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public catalog reads {@code type} the way a visitor writes it. The SQL
 * predicates of {@link PublicCatalogSearch} run against PostgreSQL in CI
 * ({@code immutable_unaccent}, {@code regexp_replace}); this locks the parsing.
 */
class PublicCatalogSearchTest {

    @ParameterizedTest
    @CsvSource({
            "SERVICE, SERVICE",
            "servicio, SERVICE",
            "Servicios, SERVICE",
            "SERVÍCIO, SERVICE",
            "PRODUCT, PRODUCT",
            "producto, PRODUCT",
            "Productos, PRODUCT"
    })
    void parseType_acceptsCodesAndSpanishWords(String raw, ProductType expected) {
        assertThat(PublicCatalogSearch.parseType(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "todo", "xyz"})
    void parseType_unknownOrBlank_meansNoFilter(String raw) {
        assertThat(PublicCatalogSearch.parseType(raw)).isNull();
    }
}
