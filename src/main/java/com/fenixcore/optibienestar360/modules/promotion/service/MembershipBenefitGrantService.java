package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignAllyBenefitDto;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipBenefitGrantDto;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant.GrantStatus;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipBenefitGrantRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import com.fenixcore.optibienestar360.modules.validator.dto.ValidationResultDto;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ally vouchers of campaign promotions (V257, hub ADR 0038 §7): granted when
 * an ALLY_BENEFIT slot is CONFIRMED, cancelled when the slot is freed or the
 * promotion lost, expired by the daily job, listed in the ally validator and
 * the member portal, and redeemed by the ally when it registers the usage.
 * The cost is absorbed by the ally and only recorded (reconciliation report).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MembershipBenefitGrantService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MembershipBenefitGrantRepository repository;
    private final AllyUserRepository allyUserRepository;
    private final MembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final CampaignRepository campaignRepository;
    private final PromotionRepository promotionRepository;
    private final CurrencyRepository currencyRepository;
    private final CurrencyConversionService currencyConversionService;
    private final ValidatorCacheService validatorCacheService;

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    /** The slot of an ALLY_BENEFIT promotion was just CONFIRMED: the member gets the voucher (once). */
    @Transactional
    public MembershipBenefitGrant grantFor(PromotionRedemption redemption) {
        Promotion promotion = redemption.getPromotion();
        MembershipPromotion mp = redemption.getMembershipPromotion();
        if (promotion == null || !promotion.isAllyBenefit() || mp == null) {
            return null;
        }
        if (mp.getId() != null && repository.findByMembershipPromotion_Id(mp.getId()).isPresent()) {
            return null;
        }
        MembershipBenefitGrant grant = new MembershipBenefitGrant();
        grant.setMembershipPromotion(mp);
        grant.setMembership(mp.getMembership());
        grant.setPromotion(promotion);
        grant.setAlly(promotion.getAlly());
        grant.setAllyProduct(promotion.getAllyProduct());
        grant.setBenefitType(promotion.getBenefitType());
        grant.setBenefitPct(promotion.getBenefitPct());
        grant.setBenefitAmount(promotion.getBenefitAmount());
        grant.setBenefitCurrency(promotion.getBenefitCurrency());
        grant.setQuantity(promotion.getBenefitQuantity());
        grant.setRemainingQty(promotion.getBenefitQuantity());
        grant.setGrantStatus(GrantStatus.AVAILABLE);
        grant.setGrantedAt(redemption.getConfirmedAt() != null ? redemption.getConfirmedAt() : Instant.now());
        grant.setExpiresAt(redemption.getUsageEndsAt());
        MembershipBenefitGrant saved = repository.save(grant);
        validatorCacheService.evictForMembership(mp.getMembership());
        return saved;
    }

    /** The slot was freed or the promotion lost: a voucher not redeemed yet is CANCELED. */
    @Transactional
    public void cancelFor(MembershipPromotion mp) {
        if (mp == null || mp.getId() == null) {
            return;
        }
        repository.findByMembershipPromotion_Id(mp.getId())
                .filter(g -> g.getGrantStatus() == GrantStatus.AVAILABLE)
                .ifPresent(g -> {
                    g.setGrantStatus(GrantStatus.CANCELED);
                    validatorCacheService.evictForMembership(g.getMembership());
                });
    }

    /** Whether at least one unit of the promotion's voucher was already redeemed. */
    public boolean anyRedeemed(MembershipPromotion mp) {
        if (mp == null || mp.getId() == null) {
            return false;
        }
        return repository.findByMembershipPromotion_Id(mp.getId())
                .map(g -> g.getRemainingQty() < g.getQuantity())
                .orElse(false);
    }

    /**
     * The campaign just closed: its AVAILABLE vouchers without an expiry (an
     * open-ended campaign whose benefit lasts the campaign dates) now expire
     * at the close. The job turns them EXPIRED once that instant passes.
     *
     * @return how many vouchers got the expiry
     */
    @Transactional
    public int expireAtClose(Campaign campaign, Instant closedAt) {
        int capped = 0;
        for (MembershipBenefitGrant g : repository.findWithoutExpiry(campaign, GrantStatus.AVAILABLE)) {
            g.setExpiresAt(closedAt);
            validatorCacheService.evictForMembership(g.getMembership());
            capped++;
        }
        return capped;
    }

    /** Job: AVAILABLE vouchers past their expiry become EXPIRED. */
    @Transactional
    public int expireAvailable(Instant now) {
        int expired = 0;
        for (MembershipBenefitGrant g : repository.findExpiring(GrantStatus.AVAILABLE, now)) {
            g.setGrantStatus(GrantStatus.EXPIRED);
            expired++;
        }
        return expired;
    }

    // ─── Redeem ─────────────────────────────────────────────────────────────

    /** What the member pays for the redeemed line after the voucher: {@code null} amount = no co-pay recorded. */
    public record Copay(BigDecimal amount, String currencyCode) {}

    /**
     * Redeems one unit of {@code grantUuid} for a usage the ally is
     * registering (row lock first). The voucher must be AVAILABLE and not
     * expired, of the same ally and membership as the usage, and for the same
     * offer. At zero units left it becomes REDEEMED. The validator cache of
     * the member is evicted.
     */
    @Transactional
    public MembershipBenefitGrant redeem(UUID grantUuid, Membership membership, Ally ally, AllyProduct allyProduct) {
        MembershipBenefitGrant grant = repository.findForUpdateByUuid(grantUuid)
                .orElseThrow(() -> new NoSuchElementException("benefit_grant.not_found"));
        Instant now = Instant.now();
        if (grant.getGrantStatus() != GrantStatus.AVAILABLE || grant.getRemainingQty() <= 0) {
            throw new IllegalArgumentException("benefit_grant.not_available");
        }
        if (grant.getExpiresAt() != null && !grant.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("benefit_grant.expired");
        }
        if (ally == null || !grant.getAlly().getId().equals(ally.getId())) {
            throw new IllegalArgumentException("benefit_grant.other_ally");
        }
        if (membership == null || !grant.getMembership().getId().equals(membership.getId())) {
            throw new IllegalArgumentException("benefit_grant.other_membership");
        }
        if (allyProduct != null && !grant.getAllyProduct().getId().equals(allyProduct.getId())) {
            throw new IllegalArgumentException("benefit_grant.other_product");
        }
        grant.setRemainingQty(grant.getRemainingQty() - 1);
        if (grant.getRemainingQty() == 0) {
            grant.setGrantStatus(GrantStatus.REDEEMED);
            grant.setRedeemedAt(now);
        }
        validatorCacheService.evictForMembership(membership);
        return grant;
    }

    /**
     * The co-pay of the redeemed line: FREE = 0; PERCENTAGE = the co-pay
     * reduced by the percentage; FLAT = the co-pay minus the amount converted
     * to the co-pay currency (never below 0; a missing rate leaves the co-pay
     * untouched, ADR 0015 §7). Without a co-pay only FREE records one (0 in
     * {@code fallbackCurrencyCode}, when known).
     */
    public Copay copayAfter(MembershipBenefitGrant grant, BigDecimal copayAmount, String copayCurrencyCode,
                            String fallbackCurrencyCode) {
        return switch (grant.getBenefitType()) {
            case FREE -> {
                String code = copayCurrencyCode != null ? copayCurrencyCode : fallbackCurrencyCode;
                yield code == null ? new Copay(null, null) : new Copay(BigDecimal.ZERO.setScale(2), code);
            }
            case PERCENTAGE -> copayAmount == null ? new Copay(null, copayCurrencyCode)
                    : new Copay(copayAmount.multiply(HUNDRED.subtract(grant.getBenefitPct()))
                            .divide(HUNDRED, 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO), copayCurrencyCode);
            case FLAT -> copayAmount == null ? new Copay(null, copayCurrencyCode)
                    : new Copay(copayAmount.subtract(flatIn(grant, copayCurrencyCode)).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP), copayCurrencyCode);
        };
    }

    private BigDecimal flatIn(MembershipBenefitGrant grant, String currencyCode) {
        Currency from = grant.getBenefitCurrency();
        if (from == null || currencyCode == null || currencyCode.equals(from.getCode())) {
            return grant.getBenefitAmount();
        }
        Currency to = currencyRepository.findByCode(currencyCode).orElse(null);
        if (to == null) {
            return BigDecimal.ZERO;
        }
        try {
            return currencyConversionService.convert(grant.getBenefitAmount(), from, to, Instant.now()).convertedAmount();
        } catch (NoExchangeRateAvailableException ex) {
            log.warn("Benefit grant {}: no exchange rate {} -> {}; co-pay left untouched",
                    grant.getUuid(), from.getCode(), currencyCode);
            return BigDecimal.ZERO;
        }
    }

    // ─── Read ───────────────────────────────────────────────────────────────

    /**
     * Adds to a validator answer the AVAILABLE, unexpired vouchers of its
     * membership with any ally the caller operates. Computed fresh on every
     * call (never cached: the answer is shared by every ally). Validation
     * itself still records nothing.
     */
    public ValidationResultDto withGrants(ValidationResultDto result, UUID actorUserUuid) {
        return withGrants(result, actorUserUuid, null);
    }

    /**
     * Same as {@link #withGrants(ValidationResultDto, UUID)}, narrowed to one
     * ally when {@code allyUuid} is sent (the ally the portal is operating
     * as): only that ally's vouchers, and none when the caller does not
     * operate it. {@code null} = every ally of the caller.
     */
    public ValidationResultDto withGrants(ValidationResultDto result, UUID actorUserUuid, UUID allyUuid) {
        if (result == null || result.membershipUuid() == null || actorUserUuid == null) {
            return result;
        }
        Set<Long> allyIds = allyUserRepository.findActiveByUserUuid(actorUserUuid).stream()
                .map(AllyUser::getAlly)
                .filter(ally -> allyUuid == null || allyUuid.equals(ally.getUuid()))
                .map(Ally::getId)
                .collect(Collectors.toSet());
        if (allyIds.isEmpty()) {
            return result.withBenefitGrants(List.of());
        }
        Membership membership = membershipRepository.findByUuid(result.membershipUuid()).orElse(null);
        if (membership == null) {
            return result.withBenefitGrants(List.of());
        }
        Instant now = Instant.now();
        List<MembershipBenefitGrantDto> grants = repository
                .findForMembershipAndAllies(membership.getId(), GrantStatus.AVAILABLE, allyIds).stream()
                .filter(g -> g.isUsableAt(now))
                .map(MembershipBenefitGrantService::toDto)
                .toList();
        return result.withBenefitGrants(grants);
    }

    /** The caller member's vouchers (any status), newest first ({@code GET /v1/me/benefit-grants}). */
    public List<MembershipBenefitGrantDto> forUser(UUID userUuid) {
        return memberRepository.findByUserUuid(userUuid)
                .flatMap(member -> membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId()))
                .map(membership -> repository.findByMembership_IdOrderByGrantedAtDesc(membership.getId()).stream()
                        .map(MembershipBenefitGrantService::toDto)
                        .toList())
                .orElse(List.of());
    }

    /** Reconciliation of a campaign's vouchers per ally-benefit promotion ({@code GET /v1/admin/campaigns/{uuid}/ally-benefits}). */
    public List<CampaignAllyBenefitDto> allyBenefits(UUID campaignUuid) {
        Campaign campaign = campaignRepository.findByUuid(campaignUuid)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
        Map<Long, Map<GrantStatus, long[]>> byPromotion = new HashMap<>();
        for (Object[] row : repository.countByPromotionAndStatus(campaign)) {
            Long promotionId = (Long) row[0];
            GrantStatus status = (GrantStatus) row[1];
            long grants = ((Number) row[2]).longValue();
            long units = row[3] == null ? 0L : ((Number) row[3]).longValue();
            byPromotion.computeIfAbsent(promotionId, k -> new EnumMap<>(GrantStatus.class))
                    .put(status, new long[]{grants, units});
        }
        return promotionRepository.findByCampaignAndActiveTrueOrderByCreatedAtAsc(campaign).stream()
                .filter(Promotion::isAllyBenefit)
                .map(p -> {
                    Map<GrantStatus, long[]> counts = byPromotion.getOrDefault(p.getId(), Map.of());
                    long available = count(counts, GrantStatus.AVAILABLE);
                    long redeemed = count(counts, GrantStatus.REDEEMED);
                    long expired = count(counts, GrantStatus.EXPIRED);
                    long canceled = count(counts, GrantStatus.CANCELED);
                    long units = counts.values().stream().mapToLong(v -> v[1]).sum();
                    return new CampaignAllyBenefitDto(DisplayRefs.ref(p.getAlly()), DisplayRefs.ref(p),
                            available + redeemed + expired + canceled, available, redeemed, expired, canceled, units);
                })
                .toList();
    }

    private static long count(Map<GrantStatus, long[]> counts, GrantStatus status) {
        long[] v = counts.get(status);
        return v == null ? 0L : v[0];
    }

    static MembershipBenefitGrantDto toDto(MembershipBenefitGrant g) {
        Promotion promotion = g.getPromotion();
        return new MembershipBenefitGrantDto(
                g.getUuid(),
                DisplayRefs.ref(promotion),
                promotion != null ? DisplayRefs.ref(promotion.getCampaign()) : null,
                DisplayRefs.ref(g.getAlly()),
                PromotionService.allyProductRef(g.getAllyProduct()),
                g.getBenefitType(),
                g.getBenefitPct(),
                g.getBenefitAmount(),
                DisplayRefs.ref(g.getBenefitCurrency()),
                PromotionService.currencyCode(g.getBenefitCurrency()),
                g.getQuantity(),
                g.getRemainingQty(),
                g.getGrantStatus(),
                g.getGrantedAt(),
                g.getExpiresAt(),
                g.getRedeemedAt());
    }
}
