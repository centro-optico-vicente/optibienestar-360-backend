package com.fenixcore.optibienestar360.modules.member.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Insert-only audit trail for the permanent member↔promoter link (V35, v2
 * PDF 2.a). {@link Member#getPromoter()} is set once at enrollment and only
 * ever changes through {@code POST /v1/admin/members/{uuid}/assign-promoter};
 * every such change writes exactly one row here.
 *
 * <p>Rows are never updated or soft-deleted — the {@code is_active}/{@code
 * status} columns come from {@link BaseEntity} for schema consistency but stay
 * at their defaults. {@code createdAt} is the moment of the reassignment.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "member_promoter_assignments")
@AttributeOverride(name = "id", column = @Column(name = "member_promoter_assignments_id", nullable = false, updatable = false))
public class MemberPromoterAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** NULL when the member had no promoter before (first assignment / back-fill). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_promoter_id")
    private Promoter fromPromoter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_promoter_id", nullable = false)
    private Promoter toPromoter;

    /** Admin who performed the reassignment; NULL only for system-driven moves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;
}
