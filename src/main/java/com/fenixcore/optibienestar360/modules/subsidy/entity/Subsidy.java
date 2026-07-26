package com.fenixcore.optibienestar360.modules.subsidy.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Subsidy / exoneration granted to a {@link Member} (v2 PDF item #1, V41).
 * Waives or reduces the titular's fees for special profiles (foundations,
 * churches, low-income) while keeping a formal audit trail
 * ({@link SubsidyAuditLog}).
 *
 * <p>Coverage is expressed as two <b>independent</b> percentages, one per fee
 * type:</p>
 * <ul>
 *   <li>{@code null} — that fee is not covered by this subsidy.</li>
 *   <li>{@code 100} — full exoneration of that fee.</li>
 *   <li>{@code 0 < X < 100} — partial subsidy of X% off that fee.</li>
 * </ul>
 * A subsidy must cover at least one fee (V41 CHECK
 * {@code chk_subsidies_covers_something}).
 *
 * <p>{@link #validFrom}/{@link #validUntil} bound the subsidy in time
 * ({@code validUntil == null} = indefinite). {@link #maxExoneratedBeneficiaries}
 * caps how many beneficiaries this subsidy may exonerate — distinct from the
 * plan's own {@code max_beneficiaries} — and is enforced service-side over
 * {@link #beneficiaries} (the CHECK can't count sibling rows).</p>
 *
 * <p>Revocation is a soft-delete ({@code is_active = false}); the audit log
 * keeps the history regardless.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subsidies")
@AttributeOverride(name = "id", column = @Column(name = "subsidies_id", nullable = false, updatable = false))
public class Subsidy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** % off the monthly fee. {@code null} = monthly not covered; 100 = full exoneration. */
    @Column(name = "monthly_percentage", precision = 5, scale = 2)
    private BigDecimal monthlyPercentage;

    /** % off the inscription fee. {@code null} = inscription not covered; 100 = full exoneration. */
    @Column(name = "inscription_percentage", precision = 5, scale = 2)
    private BigDecimal inscriptionPercentage;

    /** Cap on exonerated beneficiaries — distinct from the plan's max. {@code null} = plan-bounded. */
    @Column(name = "max_exonerated_beneficiaries")
    private Integer maxExoneratedBeneficiaries;

    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason;

    /** The admin (user) who authorized the subsidy. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_by")
    private User authorizedBy;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** {@code null} = indefinite. */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @OneToMany(mappedBy = "subsidy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubsidyBeneficiary> beneficiaries = new ArrayList<>();

    /** Adds a child exoneration and keeps both sides of the association consistent. */
    public void addBeneficiary(SubsidyBeneficiary row) {
        row.setSubsidy(this);
        this.beneficiaries.add(row);
    }
}
