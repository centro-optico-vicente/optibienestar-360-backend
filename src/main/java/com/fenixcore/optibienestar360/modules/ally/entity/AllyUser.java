package com.fenixcore.optibienestar360.modules.ally.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * N:M membership user ↔ ally (V12). Defines which users can operate on
 * which {@link Ally}, with what intra-ally authority level
 * ({@link AllyRole}).
 *
 * <p>{@link AllyRole} is orthogonal to the user's global role assignment
 * in {@code user_roles} (typically {@code ALIADO}). The service layer
 * combines both: the user needs (a) the global {@code ALLY_*} permission
 * required AND (b) an active membership on the target ally with a
 * sufficient {@code allyRole}.</p>
 *
 * <p>At most ONE {@code primary=true} active row per ally — guaranteed by
 * the partial unique index in the V12 migration.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "ally_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ally_id", "user_id"})
)
@AttributeOverride(name = "id", column = @Column(name = "ally_users_id", nullable = false, updatable = false))
public class AllyUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_id", nullable = false)
    private Ally ally;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "ally_role", length = 20, nullable = false)
    private AllyRole allyRole = AllyRole.STAFF;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    public enum AllyRole {
        /** Edits the ally profile, proposes / withdraws services, signs agreements, manages other ally_users. */
        OWNER,
        /** Day-to-day operations (validate affiliates, register benefit usage). */
        STAFF,
        /** Read-only — reports, dashboard. */
        VIEWER
    }
}
