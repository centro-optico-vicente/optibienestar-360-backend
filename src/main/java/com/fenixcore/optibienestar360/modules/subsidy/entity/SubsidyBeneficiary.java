package com.fenixcore.optibienestar360.modules.subsidy.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
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

import java.math.BigDecimal;

/**
 * Per-beneficiary exoneration under a {@link Subsidy} (V41). Optional — a
 * subsidy may exonerate only the titular, or additionally list specific
 * beneficiaries, each with its own two-percentage coverage
 * ({@code null}/100/partial, same semantics as {@link Subsidy}).
 *
 * <p>The number of rows per subsidy is bounded by
 * {@code subsidies.max_exonerated_beneficiaries} (or the plan's own cap),
 * enforced in {@code SubsidiesService}. UNIQUE {@code (subsidy_id,
 * beneficiary_id)} keeps a beneficiary from being listed twice under the same
 * subsidy.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subsidy_beneficiaries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"subsidy_id", "beneficiary_id"}))
@AttributeOverride(name = "id", column = @Column(name = "subsidy_beneficiaries_id", nullable = false, updatable = false))
public class SubsidyBeneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subsidy_id", nullable = false)
    private Subsidy subsidy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    /** % off this beneficiary's monthly share. {@code null} = not covered; 100 = full. */
    @Column(name = "monthly_percentage", precision = 5, scale = 2)
    private BigDecimal monthlyPercentage;

    /** % off this beneficiary's extra inscription fee. {@code null} = not covered; 100 = full. */
    @Column(name = "inscription_percentage", precision = 5, scale = 2)
    private BigDecimal inscriptionPercentage;
}
