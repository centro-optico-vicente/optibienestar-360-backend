package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payment reason/category catalog (V115, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). FK target of
 * {@code payments.payment_type_id} — the REASON money moved (membership fee,
 * commission payout, bonus, ...), never to be confused with
 * {@link PaymentMethod} (the line-level HOW).
 *
 * <p>{@code direction} is a real fact of the category itself (IN/OUT), not a
 * disguised method marker — see V115 header comment.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_categories")
@AttributeOverride(name = "id", column = @Column(name = "payment_categories_id", nullable = false, updatable = false))
public class PaymentCategory extends BaseEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 80, nullable = false)
    private String name;

    @Column(length = 255)
    private String description;

    /** {@code IN} or {@code OUT} — CHECK-enforced at the DB (V115). */
    @Column(length = 10, nullable = false)
    private String direction;
}
