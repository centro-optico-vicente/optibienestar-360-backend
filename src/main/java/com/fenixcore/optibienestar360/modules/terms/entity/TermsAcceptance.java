package com.fenixcore.optibienestar360.modules.terms.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records that {@link #user} accepted {@link #termsVersion} at
 * {@link #acceptedAt}. Idempotent by the {@code (user_id, terms_versions_id)}
 * UNIQUE constraint — {@code TermsAcceptanceService.accept} relies on that
 * instead of a pre-check.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "terms_acceptances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "terms_versions_id"})
)
@AttributeOverride(name = "id", column = @Column(name = "terms_acceptances_id", nullable = false, updatable = false))
public class TermsAcceptance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terms_versions_id", nullable = false)
    private TermsVersion termsVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt = Instant.now();
}
