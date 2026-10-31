package com.fenixcore.optibienestar360.modules.ally.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * One opening-hours range of an ally (V214, hub ADR 0031 §7): an ISO day of
 * the week (1 = Monday … 7 = Sunday) and a wall-clock range in the business
 * zone. A day may have several ranges; they never overlap (checked by
 * {@code OpeningHoursRules}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ally_opening_hours")
@AttributeOverride(name = "id", column = @Column(name = "ally_opening_hours_id", nullable = false, updatable = false))
public class AllyOpeningHour extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_id", nullable = false)
    private Ally ally;

    /** ISO-8601 day: 1 = Monday … 7 = Sunday. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;
}
