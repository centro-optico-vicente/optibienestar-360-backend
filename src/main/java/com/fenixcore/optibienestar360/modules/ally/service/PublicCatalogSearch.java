package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.catalog.entity.State;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Plain-text filters for the anonymous catalog (hub ADR 0026): visitors and
 * leads search by what they read on screen — a city name, an ally's name,
 * RIF or number, a category name, "servicio" / "producto" — never by UUID.
 *
 * <p>Every comparison is case- and accent-insensitive through
 * {@code immutable_unaccent(lower(…))} (same expression as
 * {@code SearchSpecifications}, so the trigram indexes on {@code allies.name}
 * and {@code products.name} apply). City and state are LEFT-joined: an ally
 * without a city still matches by any other field.</p>
 */
final class PublicCatalogSearch {

    /** A Venezuelan RIF / cédula as typed: optional letter, digits, any dashes or spaces. */
    private static final Pattern DOCUMENT = Pattern.compile("^([VEJGPC])?(\\d{5,10})$");

    private PublicCatalogSearch() {
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * {@code type} as a visitor would write it: {@code SERVICE}/{@code PRODUCT}
     * or Spanish {@code servicio(s)}/{@code producto(s)}, any case and accent.
     * Anything else means "no type filter" rather than a 400 — a typo must
     * never break a public page.
     */
    static ProductType parseType(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String value = fold(raw);
        if (value.startsWith("serv")) {
            return ProductType.SERVICE;
        }
        if (value.startsWith("prod")) {
            return ProductType.PRODUCT;
        }
        return null;
    }

    // ─── Offers (AllyProduct) ────────────────────────────────────────────────

    /** Free text over everything a visitor may type: product, category, ally, city or state. */
    static Specification<AllyProduct> offerMatchesAnything(String q) {
        return (root, query, cb) -> {
            Join<AllyProduct, Product> product = root.join("product");
            Join<Product, ProductCategory> category = product.join("productCategory");
            Join<AllyProduct, Ally> ally = root.join("ally");
            Join<Ally, City> city = ally.join("city", JoinType.LEFT);
            Join<City, State> state = city.join("state", JoinType.LEFT);
            return cb.or(
                    contains(cb, product.get("name"), q),
                    contains(cb, product.get("description"), q),
                    contains(cb, category.get("name"), q),
                    contains(cb, ally.get("name"), q),
                    contains(cb, city.get("name"), q),
                    contains(cb, state.get("name"), q));
        };
    }

    /** Category by name ("lentes") or code ("LENTES_Y_MONTURAS"). */
    static Specification<AllyProduct> offerCategoryMatches(String text) {
        return (root, query, cb) -> categoryMatches(cb, root.join("product").join("productCategory"), text);
    }

    /** The ally's city or state by name ("acarigua", "portuguesa"). */
    static Specification<AllyProduct> offerCityMatches(String text) {
        return (root, query, cb) -> {
            Join<Ally, City> city = root.join("ally").join("city", JoinType.LEFT);
            return cityMatches(cb, city, text);
        };
    }

    /** The ally by name, RIF or ally number. */
    static Specification<AllyProduct> offerAllyMatches(String text) {
        return (root, query, cb) -> allyMatches(cb, root.join("ally"), text);
    }

    // ─── Allies ──────────────────────────────────────────────────────────────

    /** Ally directory free text: name, description, city or state. */
    static Specification<Ally> allyMatchesAnything(String q) {
        return (root, query, cb) -> {
            Join<Ally, City> city = root.join("city", JoinType.LEFT);
            Join<City, State> state = city.join("state", JoinType.LEFT);
            return cb.or(
                    allyMatches(cb, root, q),
                    contains(cb, root.get("description"), q),
                    contains(cb, city.get("name"), q),
                    contains(cb, state.get("name"), q));
        };
    }

    static Specification<Ally> allyCityMatches(String text) {
        return (root, query, cb) -> cityMatches(cb, root.join("city", JoinType.LEFT), text);
    }

    // ─── Shared predicates ───────────────────────────────────────────────────

    static Predicate categoryMatches(CriteriaBuilder cb, From<?, ProductCategory> category, String text) {
        return cb.or(
                contains(cb, category.get("name"), text),
                contains(cb, category.get("code"), text.trim().replace(' ', '_')));
    }

    private static Predicate cityMatches(CriteriaBuilder cb, Join<?, City> city, String text) {
        Join<City, State> state = city.join("state", JoinType.LEFT);
        return cb.or(contains(cb, city.get("name"), text), contains(cb, state.get("name"), text));
    }

    /**
     * Name (partial), RIF (with or without letter / dashes: "J-12345678-9",
     * "123456789") or the sequential ally number ("Nº de aliado").
     */
    private static Predicate allyMatches(CriteriaBuilder cb, From<?, Ally> ally, String text) {
        List<Predicate> any = new ArrayList<>();
        any.add(contains(cb, ally.get("name"), text));

        String compact = text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        var document = DOCUMENT.matcher(compact);
        if (document.matches()) {
            Expression<String> digitsOnly = cb.function("regexp_replace", String.class,
                    ally.get("taxDocumentNumber"), cb.literal("[^0-9]"), cb.literal(""), cb.literal("g"));
            Predicate number = cb.like(digitsOnly, "%" + document.group(2) + "%");
            any.add(document.group(1) == null ? number
                    : cb.and(number, cb.equal(cb.upper(ally.get("taxDocumentType")), document.group(1))));
        }
        if (compact.matches("\\d{1,9}")) {
            any.add(cb.equal(ally.get("allyNumber"), Integer.valueOf(compact)));
        }
        return cb.or(any.toArray(Predicate[]::new));
    }

    /** Case- and accent-insensitive substring match, mirroring {@code SearchSpecifications}. */
    private static Predicate contains(CriteriaBuilder cb, Expression<String> field, String text) {
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        return cb.like(
                cb.function("immutable_unaccent", String.class, cb.lower(field)),
                cb.function("immutable_unaccent", String.class, cb.literal(pattern)));
    }

    /** Lower-case, accents stripped — for parsing visitor input in Java. */
    private static String fold(String raw) {
        return Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
