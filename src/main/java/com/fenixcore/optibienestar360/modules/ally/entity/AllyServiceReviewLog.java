package com.fenixcore.optibienestar360.modules.ally.entity;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Immutable audit row of an {@link AllyService} review transition (V11
 * {@code ally_service_review_log}, v2 approval workflow). Insert-only — it does
 * <b>not</b> extend {@code BaseEntity} (no uuid / soft-delete / updated_at); the
 * table has only the columns modeled here. Each row records one hop
 * {@code from_status → to_status}, the acting user, when, and an optional comment
 * (the rejection / removal reason, or an approval note).
 *
 * <p>Shared history: the same rows back both the admin log endpoint and the
 * ally-owner log endpoint — the service layer scopes visibility, not the data.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ally_service_review_log")
public class AllyServiceReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ally_service_review_log_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_service_id", nullable = false)
    private AllyService allyService;

    /** Null only for a hypothetical creation row; every workflow hop carries both. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ReviewStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20, nullable = false)
    private ReviewStatus toStatus;

    /** The user who performed the transition (admin reviewer or the ally owner). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "action_at", nullable = false)
    private Instant actionAt;

    @Column(columnDefinition = "text")
    private String comment;
}
