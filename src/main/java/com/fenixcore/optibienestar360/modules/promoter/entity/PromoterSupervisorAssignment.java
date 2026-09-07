package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
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

/**
 * Insert-only audit trail for supervisor (re)assignment (V101) — structural
 * mirror of {@code MemberPromoterAssignment} (V35). {@link Promoter#getSupervisor()}
 * is the live pointer; every change writes exactly one row here. There is no
 * effective-from/to column: the "vigente" row for a given commission cut is
 * the most recent one with {@code createdAt <= asOf} — see
 * {@code PromoterHierarchyService#resolveSupervisorAt}.
 *
 * <p>Rows are never updated or soft-deleted — {@code is_active}/{@code
 * status} come from {@link BaseEntity} for schema consistency but stay at
 * their defaults.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_supervisor_assignments")
@AttributeOverride(name = "id", column = @Column(name = "promoter_supervisor_assignments_id", nullable = false, updatable = false))
public class PromoterSupervisorAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    /** {@code null} when the promoter had no supervisor before (first assignment / top of chain). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_supervisor_id")
    private Promoter fromSupervisor;

    /** {@code null} when the promoter is being left without a supervisor (top of chain). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_supervisor_id")
    private Promoter toSupervisor;

    /** Admin who performed the reassignment; {@code null} only for system-driven moves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;
}
