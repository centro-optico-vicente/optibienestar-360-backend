package com.fenixcore.optisaludplus.modules.ally.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
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
 * Membresía N:M user ↔ ally (V12). Define qué usuarios pueden operar sobre
 * cuál {@link Ally} y con qué nivel de autoridad intra-aliado
 * ({@link AllyRole}).
 *
 * <p>{@link AllyRole} es ortogonal al rol global del usuario en
 * {@code user_roles} (típicamente {@code ALIADO}). El service layer combina
 * ambos: el usuario necesita (a) el permiso global {@code ALLY_*} requerido
 * Y (b) una membresía activa sobre el ally afectado con
 * {@code allyRole} suficiente.</p>
 *
 * <p>A lo sumo UN {@code primary=true} activo por ally — garantizado por
 * partial unique index en la migración V12.</p>
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
        /** Edita perfil del ally, propone/retira services, firma agreements, gestiona otros ally_users. */
        OWNER,
        /** Operaciones del día a día (validar afiliados, registrar uso de beneficios). */
        STAFF,
        /** Solo lectura — reportes, dashboard. */
        VIEWER
    }
}
