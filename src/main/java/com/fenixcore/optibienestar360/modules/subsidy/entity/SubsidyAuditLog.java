package com.fenixcore.optibienestar360.modules.subsidy.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Immutable audit entry for a {@link Subsidy} operation (V41) — "registro
 * formal en auditoría" required by the PDF. One row per CREATE / MODIFY /
 * REVOKE, with before/after JSONB snapshots and the acting user.
 *
 * <p>Append-only by convention: the service never updates a row. It extends
 * {@link BaseEntity} for column uniformity; {@code createdAt} is the action
 * timestamp. {@code before/after} map to {@code jsonb} via Hibernate 6's
 * {@link SqlTypes#JSON} (same pattern as {@code Notification.templateVars}).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subsidy_audit_log")
@AttributeOverride(name = "id", column = @Column(name = "subsidy_audit_log_id", nullable = false, updatable = false))
public class SubsidyAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subsidy_id", nullable = false)
    private Subsidy subsidy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private Action action;

    /** The acting user (may be {@code null} for system-driven changes). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private Map<String, Object> before;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private Map<String, Object> after;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    /** The three audited operations (V41 CHECK constraint pins these). */
    public enum Action {
        CREATED, MODIFIED, REVOKED
    }
}
