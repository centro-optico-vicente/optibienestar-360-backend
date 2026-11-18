package com.fenixcore.optibienestar360.core.util;

import java.time.Instant;

/**
 * Open-ended bounds for {@link Instant} range parameters in JPQL queries.
 *
 * <p>{@code (:from IS NULL OR x >= :from)} does not work for {@link Instant} parameters on
 * PostgreSQL: the driver sends the null untyped and the server fails with "could not determine
 * data type of parameter $n". Queries take both bounds as required and callers pass a missing
 * bound through {@link #from}/{@link #to} instead.
 */
public final class InstantBounds {

    /** Earlier than any row this application stores. */
    public static final Instant MIN = Instant.parse("1900-01-01T00:00:00Z");
    /** Later than any row this application stores. */
    public static final Instant MAX = Instant.parse("9999-12-31T00:00:00Z");

    private InstantBounds() {}

    /** The inclusive lower bound, or {@link #MIN} when there is none. */
    public static Instant from(Instant value) {
        return value != null ? value : MIN;
    }

    /** The exclusive upper bound, or {@link #MAX} when there is none. */
    public static Instant to(Instant value) {
        return value != null ? value : MAX;
    }
}
