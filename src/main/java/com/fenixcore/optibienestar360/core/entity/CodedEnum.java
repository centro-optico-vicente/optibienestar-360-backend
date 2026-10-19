package com.fenixcore.optibienestar360.core.entity;

import java.util.Arrays;

/**
 * An enum whose persisted and wire value (its {@link #code()}) differs from the
 * Java constant name. Lets code use English identifiers ({@code TermType.ADVISOR})
 * while the database and the JSON API keep the Spanish domain code
 * ({@code "ASESOR"}) defined by the business glossary.
 *
 * <p>Each implementing enum wires the code into every boundary:</p>
 * <ul>
 *   <li>JPA — a {@link com.fenixcore.optibienestar360.core.jpa.CodedEnumConverter} subclass;</li>
 *   <li>JSON — {@code @JsonValue} on {@link #code()} and a {@code @JsonCreator} factory;</li>
 *   <li>Spring MVC path variables / request params and RSQL filters — registered in
 *       {@link com.fenixcore.optibienestar360.core.config.CodedEnumConversionConfig}.</li>
 * </ul>
 */
public interface CodedEnum {

    /** The value stored in the database and exchanged through the API. */
    String code();

    /**
     * Resolves the constant whose {@link #code()} equals {@code code} (exact match).
     *
     * @throws IllegalArgumentException when no constant carries that code — Spring
     *         turns it into a 400 for request input
     */
    static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> type, String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown " + type.getSimpleName() + " code: " + code));
    }
}
