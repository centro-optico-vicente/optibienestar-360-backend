package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward.AwardStatus;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterBonusAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read queues over {@link PromoterBonusAward} (v2 PDF #5). Plural-name
 * convention matches {@code CommissionsService} — the admin queue + the promoter
 * self-service list; the write side (granting) is the singular
 * {@code BonusEvaluationService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BonusAwardsService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "status", "rewardType", "rewardCurrency", "amount", "blocksAwarded",
            "metricCount", "windowStart", "windowEnd", "evaluatedAt", "paidAt",
            "createdAt", "active", "promoter.uuid", "rule.uuid"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(PromoterBonusAward.class, Map.of(
                    "rule_Display", "rule.name",
                    "promoter_Display", "promoter.displayName"
            ));

    private static final String[] SEARCHABLE_FIELDS = {
            "ruleNameSnapshot", "payoutReference", "adminNotes"
    };

    private final PromoterBonusAwardRepository repository;
    private final PromoterRepository promoterRepository;
    private final DefaultSortResolver defaultSortResolver;
    private final ConversionEnricher conversionEnricher;
    private final CurrencyConversionService conversionService;
    private final OrganizationRepository organizationRepository;

    // ─── Admin queue ──────────────────────────────────────────────────────────

    public BonusAwardDto get(UUID uuid) {
        return BonusAwardDto.from(findManaged(uuid), conversionEnricher);
    }

    public Page<BonusAwardDto> list(Pageable pageable, String filter, String q) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "bonus_award", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "bonus_award");
        Specification<PromoterBonusAward> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "bonus_award.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(a -> BonusAwardDto.from(a, conversionEnricher));
    }

    // ─── Payout ───────────────────────────────────────────────────────────────

    /**
     * Marks a granted bonus as PAID (v2 PDF #5) — the mark-as-paid workflow
     * {@code AdminBonusAwardController} lacked entirely until now. Mirrors
     * {@code CommissionPayoutService.markPaid}: stamps {@code paidAt} +
     * {@code payoutReference}, then snapshots the exchange rate to the
     * organization's official currency as of now (ADR 0015 §5/§7 Caso A) —
     * degrading to a null snapshot rather than blocking the payout when no
     * rate is vigente.
     *
     * @throws IllegalArgumentException (422) when the award is already PAID or VOIDED
     */
    @Transactional
    public BonusAwardDto pay(UUID uuid, String payoutReference) {
        PromoterBonusAward award = findManaged(uuid);
        if (!AwardStatus.PENDING.name().equals(award.getStatus())) {
            throw new IllegalArgumentException("bonus_award.pay.not_pending");
        }

        Instant now = Instant.now();
        award.setStatus(AwardStatus.PAID.name());
        award.setPaidAt(now);
        award.setPayoutReference(payoutReference);

        var official = organizationRepository.findSingleton().getOfficialCurrency();
        if (!official.getId().equals(award.getRewardCurrency().getId())) {
            try {
                var result = conversionService.convert(award.getAmount(), award.getRewardCurrency(), official, now);
                award.setExchangeRateUsed(result.rate());
                award.setExchangeRateDate(result.rateDate());
            } catch (NoExchangeRateAvailableException noRate) {
                log.info("No exchange rate available to snapshot for bonus award {}; leaving null", uuid);
            }
        }

        return BonusAwardDto.from(award, conversionEnricher);
    }

    private PromoterBonusAward findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("bonus_award.not_found"));
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("bonus_award", pageable);
    }

    // ─── Promoter self-service ────────────────────────────────────────────────

    /** A promoter's own awards, newest first. 404 when the caller is not a promoter. */
    public Page<BonusAwardDto> listOwn(UUID userUuid, Pageable pageable) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        return repository.findByPromoterIdAndActiveTrueOrderByCreatedAtDesc(promoter.getId(), pageable)
                .map(BonusAwardDto::from);
    }

    private static Specification<PromoterBonusAward> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
