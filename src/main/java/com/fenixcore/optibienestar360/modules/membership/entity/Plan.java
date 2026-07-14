package com.fenixcore.optibienestar360.modules.membership.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Subscription product (Individual / Familiar / Corporativo). The
 * configurable catalog that {@link Membership} rows reference; pricing and
 * beneficiary rules live here.
 *
 * <p>{@code code} is the stable natural key used by service-level lookups
 * (e.g. resolving "the Individual plan" from a hardcoded string). UUID is
 * for external surfaces; integer FK for joins.</p>
 *
 * <p>Beneficiary rules:</p>
 * <ul>
 *   <li>{@code includedBeneficiaries} — covered without extra cost (0 for
 *       Individual, ~3 for Familiar, 0 for Corporativo since each person on
 *       a corporate contract is a member, not a beneficiary).</li>
 *   <li>{@code maxBeneficiaries} — hard upper bound; {@code null} = no cap
 *       (used by Corporativo).</li>
 *   <li>{@code extraBeneficiaryInscriptionFee} — one-time per beneficiary
 *       added beyond {@code includedBeneficiaries}; {@code null} means the
 *       plan does not allow extras (e.g. Individual with max = 0).</li>
 * </ul>
 *
 * <p>Publishing flag controls public-directory visibility independently of
 * {@code isActive} — admin can ready a plan in the DB without exposing it
 * (matches the V11 allies / ally_services publishing pattern).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plans")
@AttributeOverride(name = "id", column = @Column(name = "plans_id", nullable = false, updatable = false))
public class Plan extends BaseEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PlanType type;

    // ─── Pricing ────────────────────────────────────────────────────────────

    @Column(name = "inscription_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal inscriptionFee;

    @Column(name = "monthly_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal monthlyFee;

    // ─── Beneficiaries (v2 fields) ──────────────────────────────────────────

    @Column(name = "included_beneficiaries", nullable = false)
    private int includedBeneficiaries = 0;

    @Column(name = "max_beneficiaries")
    private Integer maxBeneficiaries;

    @Column(name = "extra_beneficiary_inscription_fee", precision = 10, scale = 2)
    private BigDecimal extraBeneficiaryInscriptionFee;

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    @Column(name = "grace_period_days", nullable = false)
    private int gracePeriodDays = 7;

    // ─── Publishing ─────────────────────────────────────────────────────────

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    public enum PlanType {
        INDIVIDUAL, FAMILIAR, CORPORATIVO
    }
}
