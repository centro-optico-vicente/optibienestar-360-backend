package com.fenixcore.optibienestar360.modules.affiliation.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable history row of an {@link AffiliationRequest} (V190
 * {@code affiliation_request_logs}). Insert-only — like
 * {@code AllyProductReviewLog} it does <b>not</b> extend {@code BaseEntity}.
 * A status change records {@code fromStatus → toStatus}; an edit records the
 * changed fields in {@link #changes}; both carry who, when and an optional
 * note (a rejection's reason).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "affiliation_request_logs")
public class AffiliationRequestLog {

    /** Values of {@code affiliation_request_logs.action} (V190 CHECK). */
    public enum Action { CREATED, UPDATED, APPROVED, REJECTED }

    /** Keys of each {@link #changes} entry. */
    public static final String CHANGE_FIELD = "field";
    public static final String CHANGE_FROM = "from";
    public static final String CHANGE_TO = "to";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "affiliation_request_logs_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "affiliation_request_id", nullable = false)
    private AffiliationRequest affiliationRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private Action action;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    /** Edited fields, each {@code {field, from, to}}; {@code null} on status changes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private List<Map<String, String>> changes;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    /** {@code null} for the CREATED row: the applicant is anonymous. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "action_at", nullable = false)
    private Instant actionAt;
}
