package com.fenixcore.optibienestar360.modules.campaign.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignEffectivenessDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignExceptionRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignRelaunchRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignRequest;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignPromoter;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionException;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionException.ExceptionAction;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionLink;
import com.fenixcore.optibienestar360.modules.campaign.entity.CampaignTransactionLink.LinkSource;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignPromoterRepository;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignTransactionExceptionRepository;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignTransactionLinkRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin CRUD + lifecycle operations over {@link Campaign} (V120/V124, hub
 * plan ".ai/plans/2026-09-16-commission-payouts-collections-plan.md"). Mirrors
 * the shape of {@code BonusRulesService}/{@code HierarchyOverrideTiersService}
 * for the plain CRUD half; the campaign-specific half (relaunch/clone,
 * transaction exceptions, effectiveness) lives here too since it's all the
 * same aggregate root.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "scope", "mode", "enabled", "evaluateOnlyAtEnd", "payOnlyAtEnd",
            "startsAt", "endsAt", "exclusivityGroup", "priority",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Campaign.class, Map.of());

    private static final String[] SEARCHABLE_FIELDS = {"name", "description", "exclusivityGroup"};

    private final CampaignRepository repository;
    private final CampaignPromoterRepository campaignPromoterRepository;
    private final CampaignTransactionLinkRepository linkRepository;
    private final CampaignTransactionExceptionRepository exceptionRepository;
    private final PromoterRepository promoterRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final CommissionTierRepository commissionTierRepository;
    private final CommissionBonusRuleRepository bonusRuleRepository;
    private final HierarchyOverrideTierRepository hierarchyOverrideTierRepository;
    private final DefaultSortResolver defaultSortResolver;

    // ─── Read ───────────────────────────────────────────────────────────────

    public CampaignDto get(UUID uuid) {
        return toDto(findManaged(uuid));
    }

    public Page<CampaignDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("campaign", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "campaign");
        Specification<Campaign> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "campaign.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(this::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("campaign", pageable);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "campaign", action = AuditAction.CREATE)
    public CampaignDto create(CampaignRequest req) {
        validate(req);
        Campaign campaign = new Campaign();
        apply(campaign, req);
        Campaign saved = repository.save(campaign);
        syncPromoters(saved, req.promoterUuids());
        return toDto(saved);
    }

    // ─── Update (full replace) ──────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "campaign", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CampaignDto update(UUID uuid, CampaignRequest req) {
        validate(req);
        Campaign campaign = findManaged(uuid);
        apply(campaign, req);
        syncPromoters(campaign, req.promoterUuids());
        return toDto(campaign);   // managed → dirty-check on commit
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "campaign", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    // ─── Relaunch / clone ───────────────────────────────────────────────────

    /**
     * Clones {@code name}/{@code description} and every config knob except
     * dates (left to the caller via {@code req}) plus the campaign's
     * anchored commission rules (across {@link CommissionTier}, {@link
     * CommissionBonusRule}, {@link HierarchyOverrideTier} — {@code
     * CollectionCommissionTier} isn't campaign-anchored yet, see ADR/plan
     * follow-up) so the new campaign starts with the same rate structure and
     * the admin only has to adjust what changed.
     *
     * <p>Deliberately NOT cloned: {@link CampaignPromoter} membership rows
     * (audience is re-declared for the new run — a promoter's inclusion in
     * the old campaign is execution history, not config) and any {@link
     * CampaignTransactionLink}/{@link CampaignTransactionException} (pure
     * history of the source campaign's run).</p>
     */
    @Transactional
    @Auditable(entity = "campaign", action = AuditAction.CREATE)
    public CampaignDto relaunch(UUID uuid, CampaignRelaunchRequest req) {
        Campaign source = findManaged(uuid);
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new IllegalArgumentException("campaign.dates.order");
        }

        Campaign clone = new Campaign();
        clone.setName(source.getName());
        clone.setDescription(source.getDescription());
        clone.setStartsAt(req.startsAt());
        clone.setEndsAt(req.endsAt());
        clone.setEnabled(true);
        clone.setScope(source.getScope());
        clone.setMode(source.getMode());
        clone.setEvaluateOnlyAtEnd(source.isEvaluateOnlyAtEnd());
        clone.setPayOnlyAtEnd(source.isPayOnlyAtEnd());
        clone.setTargetAmount(source.getTargetAmount());
        clone.setTargetCount(source.getTargetCount());
        clone.setExclusivityGroup(source.getExclusivityGroup());
        clone.setPriority(source.getPriority());
        Campaign saved = repository.save(clone);

        for (CommissionTier t : commissionTierRepository.findByCampaign(source)) {
            CommissionTier c = new CommissionTier();
            c.setName(t.getName());
            c.setDescription(t.getDescription());
            c.setPlanType(t.getPlanType());
            c.setPromoterType(t.getPromoterType());
            c.setThresholdCount(t.getThresholdCount());
            c.setCommissionPct(t.getCommissionPct());
            c.setFlatAmount(t.getFlatAmount());
            c.setFlatAmountCurrency(t.getFlatAmountCurrency());
            c.setCampaign(saved);
            c.setStartsAt(saved.getStartsAt());
            c.setEndsAt(saved.getEndsAt());
            c.setPeriodStrategy(t.getPeriodStrategy());
            c.setPayoutPeriodStrategy(t.getPayoutPeriodStrategy());
            c.setSettlementPeriodStrategy(t.getSettlementPeriodStrategy());
            c.setAppliesTo(t.getAppliesTo());
            commissionTierRepository.save(c);
        }

        for (CommissionBonusRule r : bonusRuleRepository.findByCampaign(source)) {
            CommissionBonusRule c = new CommissionBonusRule();
            c.setName(r.getName());
            c.setDescription(r.getDescription());
            c.setMetric(r.getMetric());
            c.setAccrual(r.getAccrual());
            c.setThresholdCount(r.getThresholdCount());
            c.setWindowStrategy(r.getWindowStrategy());
            c.setCampaignStart(saved.getStartsAt());
            c.setCampaignEnd(saved.getEndsAt());
            c.setCampaign(saved);
            c.setStartsAt(saved.getStartsAt());
            c.setEndsAt(saved.getEndsAt());
            c.setRewardType(r.getRewardType());
            c.setFlatAmount(r.getFlatAmount());
            c.setRewardPct(r.getRewardPct());
            c.setRewardCurrency(r.getRewardCurrency());
            c.setIncludeSystemPromoters(r.isIncludeSystemPromoters());
            c.setPromoterType(r.getPromoterType());
            bonusRuleRepository.save(c);
        }

        for (HierarchyOverrideTier h : hierarchyOverrideTierRepository.findByCampaign(source)) {
            HierarchyOverrideTier c = new HierarchyOverrideTier();
            c.setName(h.getName());
            c.setDescription(h.getDescription());
            c.setRank(h.getRank());
            c.setCategory(h.getCategory());
            c.setThresholdCount(h.getThresholdCount());
            c.setOverridePct(h.getOverridePct());
            c.setFlatAmount(h.getFlatAmount());
            c.setFlatAmountCurrency(h.getFlatAmountCurrency());
            c.setCampaign(saved);
            c.setStartsAt(saved.getStartsAt());
            c.setEndsAt(saved.getEndsAt());
            c.setPeriodStrategy(h.getPeriodStrategy());
            c.setPayoutPeriodStrategy(h.getPayoutPeriodStrategy());
            c.setSettlementPeriodStrategy(h.getSettlementPeriodStrategy());
            hierarchyOverrideTierRepository.save(c);
        }

        return toDto(saved);
    }

    // ─── Transaction exceptions ─────────────────────────────────────────────

    /**
     * Records a manual override AND mirrors its resolution into {@link
     * CampaignTransactionLink} (source {@code EXCEPTION_INCLUDE}/{@code
     * EXCEPTION_EXCLUDE}) plus {@code Payment.campaign}/{@code
     * Membership.campaign} for the simple-reporting mirror. An
     * {@code EXCLUDE} exception removes any pre-existing {@code AUTO} link
     * for the same transaction and clears the mirror column instead of
     * writing a positive link (there's nothing "included" to point at).
     */
    @Transactional
    @Auditable(entity = "campaign_transaction_exception", action = AuditAction.CREATE)
    public CampaignDto createException(UUID campaignUuid, CampaignExceptionRequest req) {
        Campaign campaign = findManaged(campaignUuid);
        if ((req.paymentUuid() == null) == (req.membershipUuid() == null)) {
            throw new IllegalArgumentException("campaign_transaction_exception.one_target_required");
        }

        Payment payment = req.paymentUuid() != null ? resolvePayment(req.paymentUuid()) : null;
        Membership membership = req.membershipUuid() != null ? resolveMembership(req.membershipUuid()) : null;

        CampaignTransactionException exception = new CampaignTransactionException();
        exception.setCampaign(campaign);
        exception.setPayment(payment);
        exception.setMembership(membership);
        exception.setAction(req.action());
        exception.setReason(req.reason());
        exceptionRepository.save(exception);

        mirrorException(campaign, payment, membership, req.action());
        return toDto(campaign);
    }

    @Transactional
    @Auditable(entity = "campaign_transaction_exception", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void deleteException(UUID campaignUuid, UUID exceptionUuid) {
        Campaign campaign = findManaged(campaignUuid);
        CampaignTransactionException exception = exceptionRepository.findByUuid(exceptionUuid)
                .orElseThrow(() -> new NoSuchElementException("campaign_transaction_exception.not_found"));
        if (!exception.getCampaign().getId().equals(campaign.getId())) {
            throw new NoSuchElementException("campaign_transaction_exception.not_found");
        }

        // Undo the mirror: drop the link this exception created, clear the
        // reporting column only if nothing else still points at this campaign.
        if (exception.getPayment() != null) {
            linkRepository.findByCampaignAndPayment_Id(campaign, exception.getPayment().getId())
                    .ifPresent(linkRepository::delete);
            Payment payment = exception.getPayment();
            if (payment.getCampaign() != null && payment.getCampaign().getId().equals(campaign.getId())) {
                payment.setCampaign(null);
            }
        }
        if (exception.getMembership() != null) {
            linkRepository.findByCampaignAndMembership_Id(campaign, exception.getMembership().getId())
                    .ifPresent(linkRepository::delete);
            Membership membership = exception.getMembership();
            if (membership.getCampaign() != null && membership.getCampaign().getId().equals(campaign.getId())) {
                membership.setCampaign(null);
            }
        }

        exceptionRepository.delete(exception);
    }

    private void mirrorException(Campaign campaign, Payment payment, Membership membership, ExceptionAction action) {
        LinkSource source = action == ExceptionAction.INCLUDE ? LinkSource.EXCEPTION_INCLUDE : LinkSource.EXCEPTION_EXCLUDE;

        if (payment != null) {
            CampaignTransactionLink link = linkRepository.findByCampaignAndPayment_Id(campaign, payment.getId())
                    .orElseGet(CampaignTransactionLink::new);
            link.setCampaign(campaign);
            link.setPayment(payment);
            link.setMembership(null);
            link.setSource(source);
            link.setResolvedAt(OffsetDateTime.now());
            linkRepository.save(link);
            payment.setCampaign(action == ExceptionAction.INCLUDE ? campaign : null);
        }

        if (membership != null) {
            CampaignTransactionLink link = linkRepository.findByCampaignAndMembership_Id(campaign, membership.getId())
                    .orElseGet(CampaignTransactionLink::new);
            link.setCampaign(campaign);
            link.setMembership(membership);
            link.setPayment(null);
            link.setSource(source);
            link.setResolvedAt(OffsetDateTime.now());
            linkRepository.save(link);
            membership.setCampaign(action == ExceptionAction.INCLUDE ? campaign : null);
        }
    }

    // ─── Effectiveness ──────────────────────────────────────────────────────

    /**
     * Simple JSON summary (see {@link CampaignEffectivenessDto} javadoc for
     * why this doesn't go through the PDF/XLSX/CSV reporting engine yet).
     * Sums {@link Payment#getAmount()} across every non-excluded {@link
     * CampaignTransactionLink} pointing at the campaign.
     */
    public CampaignEffectivenessDto effectiveness(UUID uuid) {
        Campaign campaign = findManaged(uuid);
        List<CampaignTransactionLink> links = linkRepository.findByCampaign(campaign).stream()
                .filter(l -> l.getSource() != LinkSource.EXCEPTION_EXCLUDE)
                .collect(Collectors.toList());

        BigDecimal total = links.stream()
                .map(CampaignTransactionLink::getPayment)
                .filter(java.util.Objects::nonNull)
                .map(Payment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long count = links.size();

        BigDecimal amountPct = campaign.getTargetAmount() != null && campaign.getTargetAmount().signum() > 0
                ? total.multiply(BigDecimal.valueOf(100)).divide(campaign.getTargetAmount(), 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal countPct = campaign.getTargetCount() != null && campaign.getTargetCount() > 0
                ? BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(campaign.getTargetCount()), 2, RoundingMode.HALF_UP)
                : null;

        return new CampaignEffectivenessDto(campaign.getUuid(), campaign.getName(), total, count,
                campaign.getTargetAmount(), campaign.getTargetCount(), amountPct, countPct);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static void validate(CampaignRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new IllegalArgumentException("campaign.dates.order");
        }
        if (req.evaluateOnlyAtEnd() && !req.payOnlyAtEnd()) {
            throw new IllegalArgumentException("campaign.evaluate_only_at_end.requires_pay_only_at_end");
        }
        if (req.scope() != Campaign.CampaignScope.ALL
                && (req.promoterUuids() == null || req.promoterUuids().isEmpty())) {
            throw new IllegalArgumentException("campaign.scope.promoters_required");
        }
    }

    private void apply(Campaign campaign, CampaignRequest req) {
        campaign.setName(req.name());
        campaign.setDescription(req.description());
        campaign.setStartsAt(req.startsAt());
        campaign.setEndsAt(req.endsAt());
        campaign.setEnabled(req.enabled() == null || req.enabled());
        campaign.setScope(req.scope());
        campaign.setMode(req.mode());
        campaign.setEvaluateOnlyAtEnd(req.evaluateOnlyAtEnd());
        campaign.setPayOnlyAtEnd(req.evaluateOnlyAtEnd() || req.payOnlyAtEnd());
        campaign.setTargetAmount(req.targetAmount());
        campaign.setTargetCount(req.targetCount());
        campaign.setExclusivityGroup(req.exclusivityGroup());
        campaign.setPriority(req.priority());
    }

    private void syncPromoters(Campaign campaign, List<UUID> promoterUuids) {
        campaignPromoterRepository.deleteByCampaign(campaign);
        if (promoterUuids == null || promoterUuids.isEmpty()) {
            return;
        }
        for (UUID promoterUuid : promoterUuids) {
            Promoter promoter = promoterRepository.findByUuid(promoterUuid)
                    .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
            CampaignPromoter cp = new CampaignPromoter();
            cp.setCampaign(campaign);
            cp.setPromoter(promoter);
            campaignPromoterRepository.save(cp);
        }
    }

    private CampaignDto toDto(Campaign campaign) {
        List<UUID> promoterUuids = campaignPromoterRepository.findByCampaign(campaign).stream()
                .map(cp -> cp.getPromoter().getUuid())
                .collect(Collectors.toList());
        return new CampaignDto(
                campaign.getUuid(), campaign.getName(), campaign.getDescription(),
                campaign.getStartsAt(), campaign.getEndsAt(), campaign.isEnabled(),
                campaign.getScope(), campaign.getMode(), campaign.isEvaluateOnlyAtEnd(), campaign.isPayOnlyAtEnd(),
                campaign.getTargetAmount(), campaign.getTargetCount(), campaign.getExclusivityGroup(),
                campaign.getPriority(), campaign.isActive(), promoterUuids);
    }

    private Campaign findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private Payment resolvePayment(UUID uuid) {
        return paymentRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("payment.not_found"));
    }

    private Membership resolveMembership(UUID uuid) {
        return membershipRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
    }

    private static Specification<Campaign> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
