package com.fenixcore.optibienestar360.core.util;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Inclusive {@code dateFrom}/{@code dateTo} range filters for operational and
 * transactional lists (never for audit logs). Days are business-timezone days
 * ({@link AppTimeZone#ZONE}, America/Caracas): for an {@link Instant} column the
 * range is {@code >= dateFrom 00:00} and {@code < (dateTo + 1) 00:00}; for a
 * {@link LocalDate} column it is a plain inclusive between. Null bounds are no-ops,
 * so these are safe to chain unconditionally.
 */
public final class DateRangeSpecifications {

    private DateRangeSpecifications() {}

    /** Range over an {@link Instant} attribute (e.g. {@code createdAt}, {@code paymentDate}). */
    public static <T> Specification<T> instantBetween(String field, LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>(2);
            if (dateFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.<Instant>get(field),
                        dateFrom.atStartOfDay(AppTimeZone.ZONE).toInstant()));
            }
            if (dateTo != null) {
                preds.add(cb.lessThan(root.<Instant>get(field),
                        dateTo.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant()));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** Range over a {@link LocalDate} attribute (e.g. {@code enrolledAt}, {@code startDate}). */
    public static <T> Specification<T> dateBetween(String field, LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>(2);
            if (dateFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.<LocalDate>get(field), dateFrom));
            }
            if (dateTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.<LocalDate>get(field), dateTo));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * Validity-window overlap over two {@link LocalDate} attributes: matches rows whose
     * {@code [startField, endField]} intersects {@code [dateFrom, dateTo]}. A null
     * {@code endField} value means open-ended; a null {@code startField} value means
     * "since forever".
     */
    public static <T> Specification<T> overlaps(String startField, String endField, LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>(2);
            if (dateTo != null) {
                preds.add(cb.or(cb.isNull(root.get(startField)),
                        cb.lessThanOrEqualTo(root.<LocalDate>get(startField), dateTo)));
            }
            if (dateFrom != null) {
                preds.add(cb.or(cb.isNull(root.get(endField)),
                        cb.greaterThanOrEqualTo(root.<LocalDate>get(endField), dateFrom)));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * Same as {@link #overlaps} for two {@link java.time.OffsetDateTime} attributes
     * (e.g. a campaign's {@code startsAt}/{@code endsAt}). Null start = since forever,
     * null end = open-ended.
     */
    public static <T> Specification<T> overlapsOffset(String startField, String endField, LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>(2);
            if (dateTo != null) {
                preds.add(cb.or(cb.isNull(root.get(startField)),
                        cb.lessThan(root.<java.time.OffsetDateTime>get(startField),
                                dateTo.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toOffsetDateTime())));
            }
            if (dateFrom != null) {
                preds.add(cb.or(cb.isNull(root.get(endField)),
                        cb.greaterThanOrEqualTo(root.<java.time.OffsetDateTime>get(endField),
                                dateFrom.atStartOfDay(AppTimeZone.ZONE).toOffsetDateTime())));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    // ─── In-memory variants (small, already-loaded sub-lists such as detail-page tabs) ─────────

    /** In-memory twin of {@link #instantBetween}: true when {@code value} falls in the Caracas-day range. */
    public static java.util.function.Predicate<Instant> instantIn(LocalDate dateFrom, LocalDate dateTo) {
        return value -> {
            if (dateFrom == null && dateTo == null) return true;
            if (value == null) return false;
            return (dateFrom == null || !value.isBefore(dateFrom.atStartOfDay(AppTimeZone.ZONE).toInstant()))
                    && (dateTo == null || value.isBefore(dateTo.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant()));
        };
    }

    /** In-memory twin of {@link #dateBetween}: inclusive range over a {@link LocalDate}. */
    public static java.util.function.Predicate<LocalDate> dateIn(LocalDate dateFrom, LocalDate dateTo) {
        return value -> {
            if (dateFrom == null && dateTo == null) return true;
            if (value == null) return false;
            return (dateFrom == null || !value.isBefore(dateFrom)) && (dateTo == null || !value.isAfter(dateTo));
        };
    }
}
