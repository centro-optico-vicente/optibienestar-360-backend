package com.fenixcore.optibienestar360.modules.auth.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Authentication / authorization principal. Holds ONLY credentials, security
 * state and the FK to its {@link Person} (civic identity). All demographic
 * fields (full_name, document, phone, locale, address, …) live in persons —
 * see ADR 0012 and V16 migration.
 *
 * <p>{@code email} stays on users as the login credential, distinct
 * conceptually from {@code person.email} which is the contact email used for
 * notifications/recibos. The two are typically equal but the separation
 * allows e.g. "log in with personal email, receive billing on corporate
 * email" without coupling.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
@AttributeOverride(name = "id", column = @Column(name = "users_id", nullable = false, updatable = false))
public class User extends BaseEntity {

    @Column(unique = true, columnDefinition = "citext")
    @JdbcTypeCode(SqlTypes.OTHER)
    private String email;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_role_id")
    private Role defaultRole;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_never_expires", nullable = false)
    private boolean passwordNeverExpires = false;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserRole> userRoles = new ArrayList<>();
}
