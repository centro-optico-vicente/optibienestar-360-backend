package com.fenixcore.optibienestar360.core.audit.entity;

import com.fenixcore.optibienestar360.core.audit.LoginAuditResult;
import com.fenixcore.optibienestar360.core.audit.LoginSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every login attempt (success and failure) plus the session lifecycle for
 * successful ones (V61, spec 16-audit.md §Login). {@code uuid} travels as
 * the {@code sid} claim in the access/refresh JWT for {@code SUCCESS} rows —
 * it's how {@code JwtAuthenticationFilter} checks {@code isValid} without a
 * second lookup by {@code jti}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "login_audit_log")
public class LoginAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_audit_log_id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "attempted_email", nullable = false, length = 255)
    private String attemptedEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoginAuditResult result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> roles;

    /** The session's active role at issuance (roles.roles_id) — distinct from {@link #roles}, which snapshots ALL roles assigned at login time. Null for rows predating this column. */
    @Column(name = "active_role_id")
    private Long activeRoleId;

    @Column(length = 10)
    private String locale;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(length = 255)
    private String hostname;

    @Column(length = 36)
    private String jti;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", length = 20)
    private LoginSessionStatus sessionStatus;

    @Column(name = "session_expires_at")
    private Instant sessionExpiresAt;

    @Column(name = "is_valid", nullable = false)
    private boolean valid = true;

    @Column(name = "logged_out_at")
    private Instant loggedOutAt;

    @Column(name = "logout_reason", length = 50)
    private String logoutReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (attemptedAt == null) {
            attemptedAt = now;
        }
        createdAt = now;
    }
}
