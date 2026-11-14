package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.AllyPartnershipCampaignDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.AllyPromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitKind;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Ally portal side of a campaign open to partnership (V257, hub ADR 0038 §8):
 * an ally lists the ACTIVE campaigns it may join (multi-ally, or exclusive to
 * itself) and adds, edits or removes its own ALLY_BENEFIT lines there. Its
 * lines are always kind ACQUISITION, its own ally and its own offer, marked
 * {@code created_by_ally}; they can only change while nobody took a slot.
 *
 * <p>The caller's ally: the {@code allyUuid} the portal sends, or the
 * caller's primary ally when omitted. VIEWER members of an ally can only
 * read.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyPartnershipService {

    private final AllyUserRepository allyUserRepository;
    private final CampaignRepository campaignRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final PromotionService promotionService;

    public List<AllyPartnershipCampaignDto> campaigns(UUID actorUserUuid, UUID allyUuid) {
        Ally ally = callerAlly(actorUserUuid, allyUuid, false);
        return campaignRepository.findOpenToAllyPartnership(LifecycleStatus.ACTIVE, ally.getId()).stream()
                .map(c -> new AllyPartnershipCampaignDto(c.getUuid(), c.getName(), c.getDescription(),
                        c.getStartsAt(), c.getEndsAt(), c.getLifecycleStatus(), c.getMaxRedemptions(),
                        c.freeSlots(), c.getAlly() != null && c.getAlly().getId().equals(ally.getId())))
                .toList();
    }

    /** The caller ally's lines in the campaign (its own proposals and the ones the admin set for it). */
    public List<PromotionDto> promotions(UUID actorUserUuid, UUID allyUuid, UUID campaignUuid) {
        Ally ally = callerAlly(actorUserUuid, allyUuid, false);
        Campaign campaign = campaignRepository.findByUuid(campaignUuid)
                .filter(Campaign::isActive)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
        return promotionRepository.findByCampaignAndActiveTrueOrderByCreatedAtAsc(campaign).stream()
                .filter(p -> p.getAlly() != null && p.getAlly().getId().equals(ally.getId()))
                .sorted(Comparator.comparing(Promotion::getName))
                .map(PromotionService::toDto)
                .toList();
    }

    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.CREATE)
    public PromotionDto create(UUID actorUserUuid, UUID allyUuid, UUID campaignUuid, AllyPromotionRequest req) {
        Ally ally = callerAlly(actorUserUuid, allyUuid, true);
        Campaign campaign = campaignRepository.findByUuid(campaignUuid)
                .filter(Campaign::isActive)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
        if (!openTo(campaign, ally)) {
            throw new IllegalArgumentException("ally_campaign.not_open");
        }
        Promotion promotion = new Promotion();
        promotion.setCampaign(campaign);
        promotionService.apply(promotion, toPromotionRequest(req), ally);
        promotion.setCreatedByAlly(true);
        return PromotionService.toDto(promotionRepository.save(promotion));
    }

    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.UPDATE, uuidArgIndex = 2)
    public PromotionDto update(UUID actorUserUuid, UUID allyUuid, UUID promotionUuid, AllyPromotionRequest req) {
        Ally ally = callerAlly(actorUserUuid, allyUuid, true);
        Promotion promotion = ownUntouched(ally, promotionUuid);
        promotionService.apply(promotion, toPromotionRequest(req), ally);
        promotion.setCreatedByAlly(true);
        return PromotionService.toDto(promotionRepository.save(promotion));
    }

    @Transactional
    @Auditable(entity = "promotion", action = AuditAction.DELETE, uuidArgIndex = 2)
    public void delete(UUID actorUserUuid, UUID allyUuid, UUID promotionUuid) {
        Ally ally = callerAlly(actorUserUuid, allyUuid, true);
        ownUntouched(ally, promotionUuid).setActive(false);
    }

    // ─── Rules ──────────────────────────────────────────────────────────────

    /** ACTIVE, open to partnership and multi-ally or exclusive to this same ally. */
    static boolean openTo(Campaign campaign, Ally ally) {
        return campaign.isActive() && campaign.isEnabled()
                && campaign.getLifecycleStatus() == LifecycleStatus.ACTIVE
                && campaign.isAllowsAllyPartnership()
                && (campaign.getAlly() == null || campaign.getAlly().getId().equals(ally.getId()));
    }

    /** One of the ally's own proposals (else 404) that never took a slot (else 422). */
    private Promotion ownUntouched(Ally ally, UUID promotionUuid) {
        Promotion promotion = promotionRepository.findByUuid(promotionUuid)
                .filter(Promotion::isActive)
                .filter(Promotion::isCreatedByAlly)
                .filter(p -> p.getAlly() != null && p.getAlly().getId().equals(ally.getId()))
                .orElseThrow(() -> new NoSuchElementException("promotion.not_found"));
        if (promotion.getRedemptionsCount() > 0 || redemptionRepository.existsByPromotion_Id(promotion.getId())) {
            throw new IllegalArgumentException("ally_campaign.promotion.has_redemptions");
        }
        return promotion;
    }

    /**
     * The ally the caller acts for: {@code allyUuid} when sent (must be one
     * of the caller's), else its primary ally. Anything else is a 403, so the
     * portal never reveals which allies exist.
     */
    private Ally callerAlly(UUID actorUserUuid, UUID allyUuid, boolean write) {
        List<AllyUser> rows = actorUserUuid == null ? List.of() : allyUserRepository.findActiveByUserUuid(actorUserUuid);
        AllyUser operator = rows.stream()
                .filter(au -> allyUuid == null || allyUuid.equals(au.getAlly().getUuid()))
                .min(Comparator.comparing((AllyUser au) -> !au.isPrimary()))
                .orElseThrow(() -> new AccessDeniedException("ally_campaign.ally.not_allowed"));
        if (write && operator.getAllyRole() == AllyRole.VIEWER) {
            throw new AccessDeniedException("ally_campaign.ally.not_allowed");
        }
        return operator.getAlly();
    }

    private static PromotionRequest toPromotionRequest(AllyPromotionRequest req) {
        return new PromotionRequest(
                req.name(), req.description(), Kind.ACQUISITION,
                null, null, null, null, null,
                null, false, req.ownQuota(), req.maxRedemptions(),
                false, false, false, false, null, null, req.planUuids(),
                BenefitKind.ALLY_BENEFIT, null, req.allyProductUuid(), req.benefitType(),
                req.benefitPct(), req.benefitAmount(), req.benefitCurrencyUuid(), req.benefitQuantity());
    }
}
