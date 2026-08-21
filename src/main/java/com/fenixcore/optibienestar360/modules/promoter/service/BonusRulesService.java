package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD over {@link CommissionBonusRule} (v2 PDF #5). Plural-name
 * convention matches {@code PromotersService}/{@code PlansService}.
 *
 * <p>The bean-validation on {@code BonusRuleRequest} covers per-field shape;
 * the cross-field invariants live here so they surface as localized 422s:</p>
 * <ul>
 *   <li>exactly one of {@code flatAmount} / {@code rewardPct} per reward type;</li>
 *   <li>PER_BLOCK accrual pays a flat amount per block — percentage rewards are
 *       only meaningful with THRESHOLD (a % of window earnings, once);</li>
 *   <li>CAMPAIGN windows require an ordered start/end; other windows carry none.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BonusRulesService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "metric", "accrual", "windowStrategy", "rewardType",
            "thresholdCount", "includeSystemPromoters",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final String[] SEARCHABLE_FIELDS = {"name", "description"};

    private final CommissionBonusRuleRepository repository;
    private final PromoterTypeRepository promoterTypeRepository;

    // ─── Read ───────────────────────────────────────────────────────────────

    public BonusRuleDto get(UUID uuid) {
        return BonusRuleDto.from(findManaged(uuid));
    }

    public Page<BonusRuleDto> list(Pageable pageable, String filter, String q,
                                     UUID promoterTypeUuid, boolean includeInactive) {
        Specification<CommissionBonusRule> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "bonus_rule.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (promoterTypeUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("promoterType").get("uuid"), promoterTypeUuid));
        }
        return repository.findAll(spec, pageable).map(BonusRuleDto::from);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "bonus_rule", action = AuditAction.CREATE)
    public BonusRuleDto create(BonusRuleRequest req) {
        validate(req);
        CommissionBonusRule rule = new CommissionBonusRule();
        apply(rule, req);
        rule.setPromoterType(resolvePromoterType(req.promoterTypeUuid()));
        return BonusRuleDto.from(repository.save(rule));
    }

    // ─── Update (full replace) ──────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "bonus_rule", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public BonusRuleDto update(UUID uuid, BonusRuleRequest req) {
        validate(req);
        CommissionBonusRule rule = findManaged(uuid);
        apply(rule, req);
        // Full replace: null clears any previously set promoter-type scope.
        rule.setPromoterType(resolvePromoterType(req.promoterTypeUuid()));
        return BonusRuleDto.from(rule);   // dirty-check flushes on commit
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "bonus_rule", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CommissionBonusRule findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("bonus_rule.not_found"));
    }

    private PromoterType resolvePromoterType(UUID uuid) {
        if (uuid == null) return null;
        return promoterTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter_type.not_found"));
    }

    private static void validate(BonusRuleRequest req) {
        if (req.rewardType() == RewardType.FLAT) {
            if (req.flatAmount() == null || req.rewardPct() != null) {
                throw new IllegalArgumentException("bonus_rule.reward.flat_xor_pct");
            }
        } else { // PERCENTAGE
            if (req.rewardPct() == null || req.flatAmount() != null) {
                throw new IllegalArgumentException("bonus_rule.reward.flat_xor_pct");
            }
            if (req.accrual() == AccrualMode.PER_BLOCK) {
                throw new IllegalArgumentException("bonus_rule.per_block.flat_only");
            }
        }

        if (req.windowStrategy() == WindowStrategy.CAMPAIGN) {
            if (req.campaignStart() == null || req.campaignEnd() == null) {
                throw new IllegalArgumentException("bonus_rule.campaign.dates_required");
            }
            if (req.campaignEnd().isBefore(req.campaignStart())) {
                throw new IllegalArgumentException("bonus_rule.campaign.dates_order");
            }
        }
    }

    private static void apply(CommissionBonusRule rule, BonusRuleRequest req) {
        rule.setName(req.name());
        rule.setDescription(req.description());
        rule.setMetric(req.metric());
        rule.setAccrual(req.accrual());
        rule.setThresholdCount(req.thresholdCount());
        rule.setWindowStrategy(req.windowStrategy());

        boolean campaign = req.windowStrategy() == WindowStrategy.CAMPAIGN;
        rule.setCampaignStart(campaign ? req.campaignStart() : null);
        rule.setCampaignEnd(campaign ? req.campaignEnd() : null);

        rule.setRewardType(req.rewardType());
        rule.setFlatAmount(req.rewardType() == RewardType.FLAT ? req.flatAmount() : null);
        rule.setRewardPct(req.rewardType() == RewardType.PERCENTAGE ? req.rewardPct() : null);
        rule.setRewardCurrency(normalizeCurrency(req.rewardCurrency()));
        rule.setIncludeSystemPromoters(Boolean.TRUE.equals(req.includeSystemPromoters()));
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) return "USD";
        return currency.trim().toUpperCase();
    }

    private static Specification<CommissionBonusRule> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
