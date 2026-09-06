package com.fenixcore.optibienestar360.modules.exchangerate.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.entity.State;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Holiday calendar rule (V93), consulted by
 * {@link com.fenixcore.optibienestar360.modules.exchangerate.service.BusinessDayCalculator}
 * to compute {@code exchange_rates.valid_from} (ADR 0015 §3).
 *
 * <p>Generalized from the legacy {@code tglo_DIA_FERIADO} pattern
 * (proyecto-iv-mh): a rule can recur (WEEKLY / MONTHLY / ANNUAL, unbounded
 * or capped at {@link #repetitionsCount}) instead of requiring a fresh row
 * every year, and its scope is a strict hierarchy derived from which of
 * {@link #country}/{@link #state}/{@link #city} are set — see
 * {@link #scope()}.</p>
 *
 * <p><strong>Operational debt, accepted:</strong> no library computes
 * movable holidays (Easter-derived Semana Santa dates in particular) — those
 * still need manual annual maintenance as one-off {@link RecurrenceType#NONE}
 * rows. Fixed-date holidays seeded as {@link RecurrenceType#ANNUAL} never
 * need re-seeding.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "holidays")
@AttributeOverride(name = "id", column = @Column(name = "holidays_id", nullable = false, updatable = false))
public class Holiday extends BaseEntity {

    @Column(length = 50)
    private String code;

    @Column(length = 150, nullable = false)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * Anchor date: for {@link RecurrenceType#NONE} the exact one-off date;
     * for {@link RecurrenceType#ANNUAL} only month+day matter; for
     * {@link RecurrenceType#WEEKLY} only the day-of-week matters; for
     * {@link RecurrenceType#MONTHLY} only the day-of-month matters.
     */
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", length = 12, nullable = false)
    private RecurrenceType recurrenceType = RecurrenceType.NONE;

    /** 0 = repeats forever when recurring; ignored when not recurring. */
    @Column(name = "repetitions_count", nullable = false)
    private int repetitionsCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private State state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    /** Recurrence semantics — see the Javadoc on each constant's usage in {@link #holidayDate}. */
    public enum RecurrenceType {
        NONE, WEEKLY, MONTHLY, ANNUAL
    }

    /** Applicability scope, derived (not persisted) from which FKs are set. */
    public enum Scope {
        GENERAL, NATIONAL, REGIONAL, LOCAL
    }

    /**
     * Derives this rule's scope from which of {@link #country}/{@link #state}/
     * {@link #city} are populated. Not persisted — always recomputed to avoid
     * drift against the three FKs, which are the single source of truth.
     */
    public Scope scope() {
        if (city != null) {
            return Scope.LOCAL;
        }
        if (state != null) {
            return Scope.REGIONAL;
        }
        if (country != null) {
            return Scope.NATIONAL;
        }
        return Scope.GENERAL;
    }
}
