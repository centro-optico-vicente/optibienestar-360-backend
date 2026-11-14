package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
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
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitKind;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.DiscountType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import jakarta.persistence.EntityManager;
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
 * Mirrors the V175/V255/V257 CHECK constraints so a bad request fails with a
 * readable message key instead of a constraint violation.
 *
 * <p>Ally benefits (V257, hub ADR 0038 §7-§8): a campaign exclusive to an ally
 * forces that ally on every line and rejects another one; in a multi-ally
 * campaign each ALLY_BENEFIT line names its own ally, whose offer the voucher
 * is for.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    private final PromotionRepository repository;
    private final CampaignRepository campaignRepository;
    private final PlanRepository planRepository;
    private final CurrencyRepository currencyRepository;
    private final AllyRepository allyRepository;
    private final AllyProductRepository allyProductRepository;
    private final EntityManager entityManager;

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
        apply(promotion, req, null);
        return toDto(repository.save(promotion));
    }

    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PromotionDto update(UUID uuid, PromotionRequest req) {
        Promotion promotion = lockForEdit(uuid);
        apply(promotion, req, null);
        return toDto(repository.save(promotion));
    }

    /** Soft delete: memberships already holding it keep their discount until it runs out. */
    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        lockForEdit(uuid).setActive(false);
    }

    /**
     * A live promotion about to be edited or removed, with the campaign row
     * and then the promotion row locked ({@code SELECT ... FOR UPDATE}, same
     * order as the slot lifecycle) and both re-read under the lock. Without it
     * the full-row update would write back the counters a concurrent
     * assignment, confirmation or job had just changed (lost update).
     */
    @Transactional
    public Promotion lockForEdit(UUID uuid) {
        Long campaignId = repository.findCampaignIdByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promotion.not_found"));
        Campaign campaign = campaignRepository.findForUpdateById(campaignId)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
        entityManager.refresh(campaign);
        Promotion promotion = repository.findForUpdateByUuid(uuid)
                .filter(Promotion::isActive)
                .orElseThrow(() -> new NoSuchElementException("promotion.not_found"));
        entityManager.refresh(promotion);
        return promotion;
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
            c.setOwnQuota(p.isOwnQuota());
            c.setMaxRedemptions(p.getMaxRedemptions());
            c.setRequiresCode(p.isRequiresCode());
            c.setAcceptsPromoterCode(p.isAcceptsPromoterCode());
            c.setAcceptsMemberCode(p.isAcceptsMemberCode());
            c.setAcceptsAllyCode(p.isAcceptsAllyCode());
            c.setReferrerRewardPct(p.getReferrerRewardPct());
            c.setReferrerRewardCycles(p.getReferrerRewardCycles());
            c.setPlans(new HashSet<>(p.getPlans()));
            c.setBenefitKind(p.getBenefitKind());
            c.setAlly(p.getAlly());
            c.setCreatedByAlly(p.isCreatedByAlly());
            c.setAllyProduct(p.getAllyProduct());
            c.setBenefitType(p.getBenefitType());
            c.setBenefitPct(p.getBenefitPct());
            c.setBenefitAmount(p.getBenefitAmount());
            c.setBenefitCurrency(p.getBenefitCurrency());
            c.setBenefitQuantity(p.getBenefitQuantity());
            repository.save(c);
        }
    }

    /**
     * Validates and copies {@code req} onto {@code promotion}. {@code fixedAlly}
     * is the caller ally of the ally portal (its own line); {@code null} for
     * the admin, whose ally comes from the request or the campaign.
     */
    public void apply(Promotion promotion, PromotionRequest req, Ally fixedAlly) {
        validate(req);
        promotion.setName(req.name().trim());
        promotion.setDescription(req.description());
        promotion.setKind(req.kind());
        BenefitKind benefitKind = benefitKindOf(req);
        promotion.setBenefitKind(benefitKind);
        Ally ally = resolveLineAlly(promotion.getCampaign(), req.allyUuid(), fixedAlly);
        promotion.setAlly(ally);
        if (benefitKind == BenefitKind.ALLY_BENEFIT) {
            applyAllyBenefit(promotion, req, ally);
        } else {
            applyDiscount(promotion, req);
        }
        promotion.setCycles(req.cycles());
        promotion.setCoversExtraBeneficiaries(req.coversExtraBeneficiaries());
        promotion.setOwnQuota(req.ownQuota());
        promotion.setMaxRedemptions(req.maxRedemptions());
        promotion.setRequiresCode(req.requiresCode());
        promotion.setAcceptsPromoterCode(req.acceptsPromoterCode());
        promotion.setAcceptsMemberCode(req.acceptsMemberCode());
        promotion.setAcceptsAllyCode(req.acceptsAllyCode());
        promotion.setReferrerRewardPct(req.referrerRewardPct());
        promotion.setReferrerRewardCycles(req.referrerRewardCycles());
        promotion.setPlans(resolvePlans(req.planUuids()));
    }

    private void applyDiscount(Promotion promotion, PromotionRequest req) {
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
        promotion.setAllyProduct(null);
        promotion.setBenefitType(null);
        promotion.setBenefitPct(null);
        promotion.setBenefitAmount(null);
        promotion.setBenefitCurrency(null);
        promotion.setBenefitQuantity(1);
    }

    /** An ALLY_BENEFIT carries the ally's offer and the voucher's benefit; never a discount. */
    private void applyAllyBenefit(Promotion promotion, PromotionRequest req, Ally ally) {
        if (ally == null) {
            throw new IllegalArgumentException("promotion.ally.required");
        }
        AllyProduct allyProduct = allyProductRepository.findByUuid(req.allyProductUuid())
                .filter(AllyProduct::isActive)
                .orElseThrow(() -> new NoSuchElementException("ally_product.not_found"));
        if (allyProduct.getAlly() == null || !allyProduct.getAlly().getId().equals(ally.getId())) {
            throw new IllegalArgumentException("promotion.ally_product.other_ally");
        }
        promotion.setAllyProduct(allyProduct);
        promotion.setDiscountType(DiscountType.PERCENTAGE);
        promotion.setDiscountPct(null);
        promotion.setDiscountAmount(null);
        promotion.setDiscountCurrency(null);
        promotion.setAppliesTo(null);
        promotion.setBenefitType(req.benefitType());
        promotion.setBenefitPct(req.benefitType() == BenefitType.PERCENTAGE ? req.benefitPct() : null);
        promotion.setBenefitAmount(req.benefitType() == BenefitType.FLAT ? req.benefitAmount() : null);
        promotion.setBenefitCurrency(req.benefitType() == BenefitType.FLAT ? resolveCurrency(req.benefitCurrencyUuid()) : null);
        promotion.setBenefitQuantity(req.benefitQuantity() != null ? req.benefitQuantity() : 1);
    }

    /**
     * The ally of a line: the portal caller's own ally, else the campaign's
     * ally when the campaign is exclusive (a different requested ally is a
     * 422), else the requested one (optional for a DISCOUNT line).
     */
    private Ally resolveLineAlly(Campaign campaign, UUID requestedAllyUuid, Ally fixedAlly) {
        Ally campaignAlly = campaign != null ? campaign.getAlly() : null;
        Ally requested = fixedAlly;
        if (requested == null && requestedAllyUuid != null) {
            requested = allyRepository.findByUuid(requestedAllyUuid)
                    .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
        }
        if (campaignAlly != null) {
            if (requested != null && !requested.getId().equals(campaignAlly.getId())) {
                throw new IllegalArgumentException("promotion.ally.campaign_exclusive");
            }
            return campaignAlly;
        }
        return requested;
    }

    private static BenefitKind benefitKindOf(PromotionRequest req) {
        return req.benefitKind() == null ? BenefitKind.DISCOUNT : req.benefitKind();
    }

    /** Mirrors V257 {@code chk_promotions_benefit_coherence} for an ALLY_BENEFIT. */
    private static void validateAllyBenefit(PromotionRequest req) {
        if (req.kind() != Kind.ACQUISITION) {
            throw new IllegalArgumentException("promotion.ally_benefit.acquisition_only");
        }
        if (req.allyProductUuid() == null || req.benefitType() == null) {
            throw new IllegalArgumentException("promotion.ally_benefit.incomplete");
        }
        if (req.discountPct() != null || req.discountAmount() != null || req.discountCurrencyUuid() != null) {
            throw new IllegalArgumentException("promotion.ally_benefit.no_discount");
        }
        boolean hasPct = req.benefitPct() != null;
        boolean hasAmount = req.benefitAmount() != null || req.benefitCurrencyUuid() != null;
        boolean coherent = switch (req.benefitType()) {
            case FREE -> !hasPct && !hasAmount;
            case PERCENTAGE -> hasPct && !hasAmount;
            case FLAT -> !hasPct && req.benefitAmount() != null && req.benefitAmount().signum() > 0
                    && req.benefitCurrencyUuid() != null;
        };
        if (!coherent) {
            throw new IllegalArgumentException("promotion.ally_benefit.type_incoherent");
        }
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
        if (benefitKindOf(req) == BenefitKind.ALLY_BENEFIT) {
            validateAllyBenefit(req);
        } else {
            validateDiscount(req);
            if (req.appliesTo() == null) {
                throw new IllegalArgumentException("promotion.applies_to.required");
            }
        }
        if (!req.ownQuota() && req.maxRedemptions() != null) {
            throw new IllegalArgumentException("promotion.max_redemptions.requires_own_quota");
        }
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
                p.isOwnQuota(),
                p.getMaxRedemptions(),
                p.getRedemptionsCount(),
                p.getConfirmedCount(),
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
                p.getCreatedAt(),
                p.getBenefitKind(),
                DisplayRefs.ref(p.getAlly()),
                allyProductRef(p.getAllyProduct()),
                p.getBenefitType(),
                p.getBenefitPct(),
                p.getBenefitAmount(),
                DisplayRefs.ref(p.getBenefitCurrency()),
                currencyCode(p.getBenefitCurrency()),
                p.getBenefitQuantity(),
                p.isCreatedByAlly());
    }

    /** An ally offer is labelled by its product (code + name). */
    static DisplayRef allyProductRef(AllyProduct allyProduct) {
        if (allyProduct == null) {
            return null;
        }
        var product = allyProduct.getProduct();
        return DisplayRef.of(allyProduct.getUuid(),
                product != null ? product.getCode() : null,
                product != null ? product.getName() : null);
    }
}
