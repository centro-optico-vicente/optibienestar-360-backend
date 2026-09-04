package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A collection-management outreach a promoter logged against one of their own
 * affiliates (V36, v2 PDF 2.b). Insert-only audit trail — never updated. Type
 * drives which fields are populated (enforced by the V36 CHECK):
 *
 * <ul>
 *   <li>{@code REMINDER} — the promoter contacted the affiliate about a due
 *       payment; {@code note} optional, promise fields NULL.</li>
 *   <li>{@code PAYMENT_PROMISE} — the affiliate committed to pay
 *       {@code promisedAmount} by {@code promisedAtDate} (both required).</li>
 *   <li>{@code NOTE} — a free-form note; promise fields NULL.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_member_contacts")
@AttributeOverride(name = "id", column = @Column(name = "promoter_member_contacts_id", nullable = false, updatable = false))
public class PromoterMemberContact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ContactType type;

    @Column(columnDefinition = "text")
    private String note;

    /** Only for {@link ContactType#PAYMENT_PROMISE}; NULL otherwise (V36 CHECK). */
    @Column(name = "promised_amount", precision = 10, scale = 2)
    private BigDecimal promisedAmount;

    /** Only for {@link ContactType#PAYMENT_PROMISE}; NULL otherwise (V36 CHECK). */
    @Column(name = "promised_at_date")
    private LocalDate promisedAtDate;

    /** Only for {@link ContactType#PAYMENT_PROMISE}; NULL otherwise (V90). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promised_currency_id")
    private Currency promisedCurrency;

    public enum ContactType {
        REMINDER, PAYMENT_PROMISE, NOTE
    }
}
