package com.fenixcore.optibienestar360.modules.membership.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.core.entity.CodedEnum;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 *   <li>{@code minMembers} — minimum number of people the plan requires
 *       ({@code null} = no minimum; 20 for Corporativo).</li>
 * </ul>
 *
 * <p>Publishing flag controls public-directory visibility independently of
 * {@code isActive} — admin can ready a plan in the DB without exposing it
 * (matches the V11 allies / ally_products publishing pattern).</p>
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

    @Convert(converter = PlanTypeConverter.class)
    @Column(length = 20, nullable = false)
    private PlanType type;

    // ─── Pricing ────────────────────────────────────────────────────────────

    @Column(name = "inscription_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal inscriptionFee;

    @Column(name = "monthly_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal monthlyFee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    // ─── Beneficiaries (v2 fields) ──────────────────────────────────────────

    @Column(name = "included_beneficiaries", nullable = false)
    private int includedBeneficiaries = 0;

    @Column(name = "max_beneficiaries")
    private Integer maxBeneficiaries;

    @Column(name = "extra_beneficiary_inscription_fee", precision = 10, scale = 2)
    private BigDecimal extraBeneficiaryInscriptionFee;

    // ─── Membership size (V177) ─────────────────────────────────────────────

    /**
     * Minimum number of people the plan requires; {@code null} = no minimum.
     * Used by Corporativo (20 per the OptiBienestar 360 client documents) and
     * enforced against a corporate contract's expected member count.
     */
    @Column(name = "min_members")
    private Integer minMembers;

    // ─── Term (V220, hub ADR 0034) ──────────────────────────────────────────

    /** The plan requires re-inscription each term; only then does a term (expiry) make sense. */
    @Column(name = "reinscription_required", nullable = false)
    private boolean reinscriptionRequired = false;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "term_mode", length = 20)
    private TermMode termMode;

    /** FIXED_DATE: month (1-12) and day (1-28) of the annual expiry. */
    @Column(name = "term_fixed_month")
    private Integer termFixedMonth;

    @Column(name = "term_fixed_day")
    private Integer termFixedDay;

    /** PERIOD: months after enrollment. */
    @Column(name = "term_period_months")
    private Integer termPeriodMonths;

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    @Column(name = "grace_period_days", nullable = false)
    private int gracePeriodDays = 7;

    // ─── Publishing ─────────────────────────────────────────────────────────

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Plan kind. The Java constants are English; {@link PlanType#code()} is the
     * Spanish value stored in {@code plans.type} / {@code commission_tiers.plan_type}
     * and exchanged through the API (JSON, RSQL {@code type==FAMILIAR}).
     */
    public enum PlanType implements CodedEnum {
        INDIVIDUAL("INDIVIDUAL"),
        FAMILY("FAMILIAR"),
        CORPORATE("CORPORATIVO");

        private final String code;

        PlanType(String code) {
            this.code = code;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @JsonCreator
        public static PlanType fromCode(String code) {
            return CodedEnum.fromCode(PlanType.class, code);
        }
    }
}
