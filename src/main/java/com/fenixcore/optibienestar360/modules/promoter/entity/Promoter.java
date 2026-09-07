package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.core.jpa.CitextJdbcType;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Sales network entity (V25). Each row is either a HUMAN promoter
 * (carries {@link #user} + {@link #person}) or the SYSTEM placeholder
 * INSTITUCION ({@link #isSystem()} = true; both FKs null) that anchors
 * commissions for enrollments lacking a referral code.
 *
 * <p>{@link #referralCode} is the short shareable tag the promoter
 * distributes; it resolves to this row at member enrollment time. The
 * service-layer code-resolver gives precedence to this table over
 * {@code members.referral_code} (V27) when a code happens to be valid
 * for both — sales-team flow wins.</p>
 *
 * <p>{@link #totalReferrals} + {@link #totalCommissionPaid} are
 * <b>cached counters</b> for the leaderboard / dashboard. They are NOT
 * the source of truth (commissions table is); the V26 → promoters
 * counter-update strategy is one of the deferred decisions documented
 * in vertical-8 § Pendientes de análisis.</p>
 *
 * <p>{@code status} is inherited from {@link BaseEntity} as a {@code
 * String}; the V25 CHECK pins it to {@link PromoterStatus} values.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoters")
@AttributeOverride(name = "id", column = @Column(name = "promoters_id", nullable = false, updatable = false))
public class Promoter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_type_id")
    private PromoterType promoterType;

    // ─── Hierarchy (V101) ───────────────────────────────────────────────────

    /**
     * The "cargo" — independent of {@link #promoterType}. NOT NULL at the DB
     * level (every promoter is backfilled to PROMOTOR); mapped nullable here
     * only because a brand-new transient instance may not have it set yet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rank_id")
    private PromoterRank rank;

    /**
     * Live pointer to this promoter's current supervisor; {@code null} means
     * top of the chain. History of changes lives in {@link
     * PromoterSupervisorAssignment}, never inferred from this column alone —
     * a commission cut must resolve the supervisor <i>vigente at that cut</i>,
     * not today's.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private Promoter supervisor;

    // ─── Display ───────────────────────────────────────────────────────────

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    @Column(columnDefinition = "text")
    private String description;

    // ─── Referral tracking ─────────────────────────────────────────────────

    @Column(name = "referral_code", length = 20, unique = true, nullable = false)
    private String referralCode;

    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    // ─── Contact ───────────────────────────────────────────────────────────

    @Column(columnDefinition = "citext")
    @JdbcType(CitextJdbcType.class)
    private String email;

    @Column(length = 30)
    private String phone;

    // ─── Snapshot counters (cache only) ────────────────────────────────────

    @Column(name = "total_referrals", nullable = false)
    private int totalReferrals = 0;

    @Column(name = "total_commission_paid", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalCommissionPaid = BigDecimal.ZERO;

    /** Values for {@link BaseEntity#getStatus()} pinned by the V25 CHECK. */
    public enum PromoterStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }
}
