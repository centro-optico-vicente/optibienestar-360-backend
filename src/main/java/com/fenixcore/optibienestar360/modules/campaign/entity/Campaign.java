package com.fenixcore.optibienestar360.modules.campaign.entity;

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

import java.time.OffsetDateTime;

/**
 * A commission/incentive campaign (hub plan
 * ".ai/plans/2026-09-16-commission-payouts-collections-plan.md" +
 * conversation spec, V120). A campaign is a time-boxed container that:
 *
 * <ul>
 *   <li>scopes which {@link com.fenixcore.optibienestar360.modules.promoter.entity.Promoter}s
 *       it applies to ({@link #scope} + {@link CampaignPromoter});</li>
 *   <li>anchors zero or more commission rules ({@code CommissionTier},
 *       {@code CommissionBonusRule}, {@code HierarchyOverrideTier},
 *       {@code CollectionCommissionTier} — via their {@code campaignId});</li>
 *   <li>tracks which payments/memberships actually counted towards it
 *       ({@link CampaignTransactionLink}), with a manual exception override
 *       ({@link CampaignTransactionException}).</li>
 * </ul>
 *
 * <p>{@link #evaluateOnlyAtEnd} / {@link #payOnlyAtEnd} — by default the
 * engine evaluates and pays as transactions come in (same continuous model
 * as the rest of the commission engine). When {@code evaluateOnlyAtEnd} is
 * {@code true} the whole campaign is scored once at {@link #endsAt} (e.g. a
 * "top seller of the quarter" campaign) — which forces {@code payOnlyAtEnd}
 * true too, since there is nothing to disburse before the campaign is
 * scored (validated in {@code CampaignService}).</p>
 *
 * <p>{@link #targetAmount} / {@link #targetCount} are optional goals a
 * campaign is scored against (e.g. "first to reach $10,000 collected", "50
 * new members") — left nullable because not every campaign shape needs a
 * numeric goal (some are pure "everything sold in this window earns X%").</p>
 *
 * <p>{@link #exclusivityGroup} + {@link #priority} let overlapping campaigns
 * declare that only one of them should apply to the same transaction (higher
 * {@link #priority} wins) — resolution lives in {@code CampaignService}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campaigns")
@AttributeOverride(name = "id", column = @Column(name = "campaigns_id", nullable = false, updatable = false))
public class Campaign extends BaseEntity {

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    /** Enable/disable switch independent of {@link BaseEntity#isActive()} (soft-delete). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 20, nullable = false)
    private CampaignScope scope = CampaignScope.ALL;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20, nullable = false)
    private CampaignMode mode = CampaignMode.GENERAL;

    /** Score the whole campaign once at {@link #endsAt} instead of continuously. */
    @Column(name = "evaluate_only_at_end", nullable = false)
    private boolean evaluateOnlyAtEnd = false;

    /** Disburse only after {@link #endsAt}. Forced {@code true} when {@link #evaluateOnlyAtEnd} is true. */
    @Column(name = "pay_only_at_end", nullable = false)
    private boolean payOnlyAtEnd = false;

    @Column(name = "target_amount", precision = 14, scale = 2)
    private java.math.BigDecimal targetAmount;

    @Column(name = "target_count")
    private Integer targetCount;

    /** Campaigns sharing a group are mutually exclusive for a given transaction — see {@link #priority}. */
    @Column(name = "exclusivity_group", length = 80)
    private String exclusivityGroup;

    /** Higher wins when resolving {@link #exclusivityGroup} conflicts. Null = no declared priority. */
    @Column(name = "priority")
    private Integer priority;

    /** Which promoters a campaign applies to. {@code ALL} ignores {@link CampaignPromoter}. */
    public enum CampaignScope {
        ALL, INCLUDE, EXCLUDE
    }

    /** Whether the campaign targets specific promoters/goals (TARGETED) or is an open, general incentive (GENERAL). */
    public enum CampaignMode {
        TARGETED, GENERAL
    }
}
