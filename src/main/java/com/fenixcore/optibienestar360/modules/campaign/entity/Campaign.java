package com.fenixcore.optibienestar360.modules.campaign.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
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
 *
 * <p>Lifecycle and quota (V256, hub ADR 0038): {@link #lifecycleStatus}
 * moves {@code DRAFT → ACTIVE ⇄ PAUSED}, {@code ACTIVE → SOLD_OUT → CLOSED};
 * only an ACTIVE campaign inside its window offers promotions. The optional
 * {@link #maxRedemptions} is shared by every promotion of the campaign;
 * {@link #redemptionsCount} counts occupied slots and {@link #confirmedCount}
 * the confirmed ones. {@link #endsAt} is optional: an open-ended campaign
 * ends when it is closed ({@link #effectiveEndsAt()}).</p>
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

    /** {@code null} = open-ended (closes by quota or by hand); see {@link #effectiveEndsAt()}. */
    @Column(name = "ends_at")
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

    /**
     * Currency {@link #targetAmount} is expressed in — required whenever
     * {@link #targetAmount} is set, {@code null} otherwise (hub plan "Parte
     * adicional — Moneda de la meta"). Lets the frontend show "meta esperada:
     * $X <moneda>" instead of assuming a fixed currency.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_amount_currency_id")
    private Currency targetAmountCurrency;

    @Column(name = "target_count")
    private Integer targetCount;

    /** Campaigns sharing a group are mutually exclusive for a given transaction — see {@link #priority}. */
    @Column(name = "exclusivity_group", length = 80)
    private String exclusivityGroup;

    /** Higher wins when resolving {@link #exclusivityGroup} conflicts. Null = no declared priority. */
    @Column(name = "priority")
    private Integer priority;

    // ─── Lifecycle and quota (V256, hub ADR 0038) ───────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 20, nullable = false)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.ACTIVE;

    /** Close condition END_DATE: the daily job closes the campaign once {@link #endsAt} passes. */
    @Column(name = "close_on_end_date", nullable = false)
    private boolean closeOnEndDate = true;

    /** Close condition QUOTA_FILLED: closes when {@link #confirmedCount} reaches {@link #maxRedemptions}. */
    @Column(name = "close_on_quota_filled", nullable = false)
    private boolean closeOnQuotaFilled = false;

    /** Quota shared by every promotion of the campaign; {@code null} = no cap. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    /** Occupied slots (pending + confirmed). */
    @Column(name = "redemptions_count", nullable = false)
    private int redemptionsCount;

    @Column(name = "confirmed_count", nullable = false)
    private int confirmedCount;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_reason", length = 20)
    private CloseReason closedReason;

    /** Who closed it by hand; {@code null} for an automatic close. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @Column(name = "closed_note", columnDefinition = "text")
    private String closedNote;

    /** Pending slots may still be confirmed until this instant after the close (exclusive with {@link #confirmGraceDays}). */
    @Column(name = "confirm_grace_until")
    private OffsetDateTime confirmGraceUntil;

    /** Pending slots may still be confirmed for N days after the close (exclusive with {@link #confirmGraceUntil}). */
    @Column(name = "confirm_grace_days")
    private Integer confirmGraceDays;

    // ─── Allies (V257, hub ADR 0038 §8) ──────────────────────────────────────

    /** Set = campaign exclusive to this ally (every promotion line is its); {@code null} = several allies. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_id")
    private Ally ally;

    /** An ally may add its own ALLY_BENEFIT lines from its portal while the campaign is ACTIVE. */
    @Column(name = "allows_ally_partnership", nullable = false)
    private boolean allowsAllyPartnership;

    // ─── Slot lifecycle (V257, hub ADR 0038 §5) ──────────────────────────────

    /** When a reserved slot becomes CONFIRMED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "quota_trigger", length = 30, nullable = false)
    private QuotaTrigger quotaTrigger = QuotaTrigger.ON_ENROLLMENT;

    /** Days a PENDING slot waits for its confirmation; {@code null} = never expires by itself. */
    @Column(name = "pending_ttl_days")
    private Integer pendingTtlDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_window", length = 30, nullable = false)
    private UsageWindow usageWindow = UsageWindow.CAMPAIGN_DATES;

    /** Days the benefit lasts after the confirmation; only for {@link UsageWindow#DAYS_AFTER_CONFIRMATION}. */
    @Column(name = "usage_window_days")
    private Integer usageWindowDays;

    /**
     * The last instant a PENDING slot reserved with {@code ownDeadline} can
     * still be confirmed: its own deadline, shortened by the confirmation
     * grace once the campaign is CLOSED (a date, or N days after the close).
     * A CLOSED campaign without grace keeps the slot's own deadline, and a
     * slot without one ends with the close itself (else it would stay PENDING
     * forever, holding its discount). {@code null} = no deadline.
     */
    public java.time.Instant pendingDeadline(java.time.Instant ownDeadline) {
        if (lifecycleStatus != LifecycleStatus.CLOSED || closedAt == null) {
            return ownDeadline;
        }
        java.time.Instant grace = confirmGraceUntil != null ? confirmGraceUntil.toInstant()
                : confirmGraceDays != null ? closedAt.plusDays(confirmGraceDays).toInstant()
                : null;
        if (grace == null) {
            return ownDeadline != null ? ownDeadline : closedAt.toInstant();
        }
        return ownDeadline == null || grace.isBefore(ownDeadline) ? grace : ownDeadline;
    }

    /**
     * When the benefit of a slot confirmed at {@code confirmedAt} stops:
     * N days later, or the campaign end (its close for an open-ended one
     * already closed, see {@link #effectiveEndsAt()}); {@code null} = no limit.
     */
    public java.time.Instant usageEndsAt(java.time.Instant confirmedAt) {
        if (usageWindow == UsageWindow.DAYS_AFTER_CONFIRMATION && usageWindowDays != null) {
            return confirmedAt.plus(java.time.Duration.ofDays(usageWindowDays));
        }
        OffsetDateTime end = effectiveEndsAt();
        return end != null ? end.toInstant() : null;
    }

    /** {@link #endsAt}, or {@link #closedAt} for an open-ended campaign; {@code null} while an open-ended one runs. */
    public OffsetDateTime effectiveEndsAt() {
        return endsAt != null ? endsAt : closedAt;
    }

    /** ACTIVE, enabled, not deleted and inside its window: the only state that offers and accepts promotions. */
    public boolean isOffering(OffsetDateTime now) {
        return isActive() && enabled
                && lifecycleStatus == LifecycleStatus.ACTIVE
                && !now.isBefore(startsAt)
                && (endsAt == null || now.isBefore(endsAt));
    }

    /** Free slots of the campaign quota; {@code null} when it has no cap. */
    public Integer freeSlots() {
        return maxRedemptions == null ? null : Math.max(0, maxRedemptions - redemptionsCount);
    }

    public boolean hasFreeSlot() {
        return maxRedemptions == null || redemptionsCount < maxRedemptions;
    }

    /** The configured close conditions, in a stable order. */
    public java.util.List<CloseCondition> closeConditions() {
        java.util.List<CloseCondition> conditions = new java.util.ArrayList<>(2);
        if (closeOnEndDate) conditions.add(CloseCondition.END_DATE);
        if (closeOnQuotaFilled) conditions.add(CloseCondition.QUOTA_FILLED);
        return conditions;
    }

    /** Moves the campaign to CLOSED; {@code by} is {@code null} for an automatic close. */
    public void close(CloseReason reason, User by, String note, OffsetDateTime at) {
        lifecycleStatus = LifecycleStatus.CLOSED;
        closedReason = reason;
        closedBy = by;
        closedNote = note;
        closedAt = at;
    }

    /** Leaves CLOSED: clears every {@code closed_*} column. */
    public void clearClose() {
        closedReason = null;
        closedBy = null;
        closedNote = null;
        closedAt = null;
    }

    /**
     * Re-derives the quota-driven state after a counter change (hub ADR 0038
     * §3): confirmed slots reaching the cap close the campaign when
     * QUOTA_FILLED is a close condition; occupied slots reaching it make an
     * ACTIVE campaign SOLD_OUT; a SOLD_OUT campaign with room again goes back
     * to ACTIVE. DRAFT and CLOSED never move here; a PAUSED campaign only
     * closes.
     */
    public void recomputeQuotaStatus(OffsetDateTime now) {
        if (lifecycleStatus == LifecycleStatus.CLOSED || lifecycleStatus == LifecycleStatus.DRAFT) {
            return;
        }
        if (maxRedemptions != null && closeOnQuotaFilled && confirmedCount >= maxRedemptions) {
            close(CloseReason.QUOTA_FILLED, null, null, now);
            return;
        }
        if (lifecycleStatus == LifecycleStatus.PAUSED) {
            return;
        }
        if (maxRedemptions != null && redemptionsCount >= maxRedemptions) {
            lifecycleStatus = LifecycleStatus.SOLD_OUT;
        } else if (lifecycleStatus == LifecycleStatus.SOLD_OUT) {
            lifecycleStatus = LifecycleStatus.ACTIVE;
        }
    }

    /** DRAFT → ACTIVE ⇄ PAUSED, ACTIVE → SOLD_OUT → CLOSED (hub ADR 0038 §3). */
    public enum LifecycleStatus {
        DRAFT, ACTIVE, PAUSED, SOLD_OUT, CLOSED
    }

    /** The conditions an admin can pick to close a campaign automatically; a manual close is always possible. */
    public enum CloseCondition {
        END_DATE, QUOTA_FILLED
    }

    public enum CloseReason {
        END_DATE, QUOTA_FILLED, MANUAL
    }

    /** When a reserved slot is confirmed (hub ADR 0038 §5). */
    public enum QuotaTrigger {
        ON_ENROLLMENT, ON_PAYMENT_REGISTERED, ON_PAYMENT_CONFIRMED
    }

    /** How long a confirmed slot keeps its benefit. */
    public enum UsageWindow {
        CAMPAIGN_DATES, DAYS_AFTER_CONFIRMATION
    }

    /** Which promoters a campaign applies to. {@code ALL} ignores {@link CampaignPromoter}. */
    public enum CampaignScope {
        ALL, INCLUDE, EXCLUDE
    }

    /** Whether the campaign targets specific promoters/goals (TARGETED) or is an open, general incentive (GENERAL). */
    public enum CampaignMode {
        TARGETED, GENERAL
    }
}
