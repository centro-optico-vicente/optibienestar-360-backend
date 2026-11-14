package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.DiscountType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD of the promotions a campaign offers members (V175, hub ADR 0018).
 * Mirrors the V175 CHECK constraints so a bad request fails with a readable
 * message key instead of a constraint violation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    private final PromotionRepository repository;
    private final CampaignRepository campaignRepository;
    private final PlanRepository planRepository;
    private final CurrencyRepository currencyRepository;

    public List<PromotionDto> listForCampaign(UUID campaignUuid) {
        return repository.findByCampaignAndActiveTrueOrderByCreatedAtAsc(findCampaign(campaignUuid)).stream()
                .map(PromotionService::toDto)
                .toList();
    }

    public PromotionDto get(UUID uuid) {
        return toDto(findManaged(uuid));
    }

    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.CREATE)
    public PromotionDto create(UUID campaignUuid, PromotionRequest req) {
        Promotion promotion = new Promotion();
        promotion.setCampaign(findCampaign(campaignUuid));
        apply(promotion, req);
        return toDto(repository.save(promotion));
    }

    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PromotionDto update(UUID uuid, PromotionRequest req) {
        Promotion promotion = findManaged(uuid);
        apply(promotion, req);
        return toDto(repository.save(promotion));
    }

    /** Soft delete: memberships already holding it keep their discount until it runs out. */
    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    /** Copies a campaign's promotions onto its relaunched clone (counters reset). */
    @Transactional
    public void cloneInto(Campaign source, Campaign target) {
        for (Promotion p : repository.findByCampaignAndActiveTrueOrderByCreatedAtAsc(source)) {
            Promotion c = new Promotion();
            c.setCampaign(target);
            c.setName(p.getName());
            c.setDescription(p.getDescription());
            c.setKind(p.getKind());
            c.setDiscountType(p.getDiscountType());
            c.setDiscountPct(p.getDiscountPct());
            c.setDiscountAmount(p.getDiscountAmount());
            c.setDiscountCurrency(p.getDiscountCurrency());
            c.setAppliesTo(p.getAppliesTo());
            c.setCycles(p.getCycles());
            c.setCoversExtraBeneficiaries(p.isCoversExtraBeneficiaries());
            c.setMaxRedemptions(p.getMaxRedemptions());
            c.setRequiresCode(p.isRequiresCode());
            c.setAcceptsPromoterCode(p.isAcceptsPromoterCode());
            c.setAcceptsMemberCode(p.isAcceptsMemberCode());
            c.setAcceptsAllyCode(p.isAcceptsAllyCode());
            c.setReferrerRewardPct(p.getReferrerRewardPct());
            c.setReferrerRewardCycles(p.getReferrerRewardCycles());
            c.setPlans(new HashSet<>(p.getPlans()));
            repository.save(c);
        }
    }

    private void apply(Promotion promotion, PromotionRequest req) {
        validate(req);
        promotion.setName(req.name().trim());
        promotion.setDescription(req.description());
        promotion.setKind(req.kind());
        DiscountType discountType = discountTypeOf(req);
        promotion.setDiscountType(discountType);
        if (discountType == DiscountType.FLAT) {
            promotion.setDiscountPct(null);
            promotion.setDiscountAmount(req.discountAmount());
            promotion.setDiscountCurrency(resolveCurrency(req.discountCurrencyUuid()));
        } else {
            promotion.setDiscountPct(req.discountPct());
            promotion.setDiscountAmount(null);
            promotion.setDiscountCurrency(null);
        }
        promotion.setAppliesTo(req.appliesTo());
        promotion.setCycles(req.cycles());
        promotion.setCoversExtraBeneficiaries(req.coversExtraBeneficiaries());
        promotion.setMaxRedemptions(req.maxRedemptions());
        promotion.setRequiresCode(req.requiresCode());
        promotion.setAcceptsPromoterCode(req.acceptsPromoterCode());
        promotion.setAcceptsMemberCode(req.acceptsMemberCode());
        promotion.setAcceptsAllyCode(req.acceptsAllyCode());
        promotion.setReferrerRewardPct(req.referrerRewardPct());
        promotion.setReferrerRewardCycles(req.referrerRewardCycles());
        promotion.setPlans(resolvePlans(req.planUuids()));
    }

    private static DiscountType discountTypeOf(PromotionRequest req) {
        return req.discountType() == null ? DiscountType.PERCENTAGE : req.discountType();
    }

    /** Mirrors V255 {@code chk_promotions_discount_coherence}. */
    private static void validateDiscount(PromotionRequest req) {
        if (discountTypeOf(req) == DiscountType.FLAT) {
            if (req.discountAmount() == null || req.discountAmount().signum() <= 0
                    || req.discountCurrencyUuid() == null || req.discountPct() != null) {
                throw new IllegalArgumentException("promotion.discount.flat_incomplete");
            }
        } else {
            if (req.discountPct() == null) {
                throw new IllegalArgumentException("promotion.discount.pct_required");
            }
            if (req.discountAmount() != null || req.discountCurrencyUuid() != null) {
                throw new IllegalArgumentException("promotion.discount.pct_with_amount");
            }
        }
    }

    private static void validate(PromotionRequest req) {
        validateDiscount(req);
        if (req.kind() == Kind.RECOVERY && req.appliesTo() != AppliesTo.MONTHLY) {
            throw new IllegalArgumentException("promotion.recovery.monthly_only");
        }
        if (req.requiresCode() && !(req.acceptsPromoterCode() || req.acceptsMemberCode() || req.acceptsAllyCode())) {
            throw new IllegalArgumentException("promotion.code.owner_required");
        }
        boolean hasRewardPct = req.referrerRewardPct() != null;
        boolean hasRewardCycles = req.referrerRewardCycles() != null;
        if (hasRewardPct != hasRewardCycles) {
            throw new IllegalArgumentException("promotion.referrer_reward.incomplete");
        }
        if (hasRewardPct && !req.acceptsMemberCode()) {
            throw new IllegalArgumentException("promotion.referrer_reward.member_code_required");
        }
    }

    private Currency resolveCurrency(UUID currencyUuid) {
        return currencyRepository.findByUuid(currencyUuid)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }

    private Set<Plan> resolvePlans(List<UUID> planUuids) {
        Set<Plan> plans = new HashSet<>();
        if (planUuids == null) return plans;
        for (UUID planUuid : planUuids) {
            plans.add(planRepository.findByUuid(planUuid)
                    .orElseThrow(() -> new NoSuchElementException("plan.not_found")));
        }
        return plans;
    }

    private Campaign findCampaign(UUID campaignUuid) {
        return campaignRepository.findByUuid(campaignUuid)
                .filter(Campaign::isActive)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private Promotion findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .filter(Promotion::isActive)
                .orElseThrow(() -> new NoSuchElementException("promotion.not_found"));
    }

    static String currencyCode(Currency currency) {
        return currency != null ? currency.getCode() : null;
    }

    static PromotionDto toDto(Promotion p) {
        return new PromotionDto(
                p.getUuid(),
                DisplayRefs.ref(p.getCampaign()),
                p.getName(),
                p.getDescription(),
                p.getKind(),
                p.getDiscountType(),
                p.getDiscountPct(),
                p.getDiscountAmount(),
                DisplayRefs.ref(p.getDiscountCurrency()),
                currencyCode(p.getDiscountCurrency()),
                p.getAppliesTo(),
                p.getCycles(),
                p.isCoversExtraBeneficiaries(),
                p.getMaxRedemptions(),
                p.getRedemptionsCount(),
                p.isRequiresCode(),
                p.isAcceptsPromoterCode(),
                p.isAcceptsMemberCode(),
                p.isAcceptsAllyCode(),
                p.getReferrerRewardPct(),
                p.getReferrerRewardCycles(),
                p.getPlans().stream()
                        .sorted(Comparator.comparing(Plan::getName))
                        .map(DisplayRefs::ref)
                        .toList(),
                p.isActive(),
                p.getCreatedAt());
    }
}
