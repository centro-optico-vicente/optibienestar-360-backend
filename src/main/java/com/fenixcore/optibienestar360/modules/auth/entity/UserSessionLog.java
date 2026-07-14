package com.fenixcore.optibienestar360.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_sessions_log")
public class UserSessionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_sessions_log_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 36)
    private String jti;

    @Column(name = "ip_address", nullable = false, columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "login_locale", length = 10)
    private String loginLocale;

    @Column(name = "login_at", nullable = false)
    private Instant loginAt = Instant.now();

    @Column(name = "logout_at")
    private Instant logoutAt;

    @Column(name = "logout_reason", length = 50)
    private String logoutReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UserSessionLog(User user, String jti, String ipAddress, String userAgent, String loginLocale) {
        this.user = user;
        this.jti = jti;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.loginLocale = loginLocale;
    }
}
