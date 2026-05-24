package com.fenixcore.optisaludplus.modules.auth.entity;

import com.fenixcore.optisaludplus.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "security_policies")
@AttributeOverride(name = "id", column = @Column(name = "security_policies_id", nullable = false, updatable = false))
public class SecurityPolicy extends BaseAuditEntity {

    @Column(name = "max_login_attempts", nullable = false)
    private int maxLoginAttempts = 5;

    @Column(name = "lockout_duration_minutes", nullable = false)
    private int lockoutDurationMinutes = 30;

    @Column(name = "days_password_expires", nullable = false)
    private int daysPasswordExpires = 90;

    @Column(name = "password_history_count", nullable = false)
    private int passwordHistoryCount = 5;

    @Column(name = "max_concurrent_sessions", nullable = false)
    private int maxConcurrentSessions = 3;
}
