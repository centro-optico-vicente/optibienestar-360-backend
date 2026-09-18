package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPeriodSummaryDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionPeriodSummary;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.mapper.CommissionMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionPeriodSummaryRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-side reads over {@link Commission}. Plural-name convention matches
 * {@code PlansService}, {@code PaymentsService}, {@code PromotersService}.
 * Distinct from the singular {@link CommissionService} that owns the
 * calc-and-persist operation: this one is for the admin queue
 * (list + single + RSQL + free-text); the singular keeps its narrow role
 * as the attribution engine wired into {@code PaymentsService.approve}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionsService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "status", "appliesTo", "periodStrategy", "currency.code",
            "amount", "commissionPct", "flatAmount", "calculationBasis",
            "periodStart", "periodEnd", "earnedAt",
            "paidAt", "voidedAt",
            "createdAt", "updatedAt", "active",
            "promoter.uuid"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Commission.class, Map.of(
                    "promoter_Display", "promoter.displayName",
                    // CommissionDto also shows `promoter_Code` (the referral code — Promoter has no generic getCode()).
                    "promoter_Code", "promoter.referralCode"
            ));

    private static final String[] SEARCHABLE_FIELDS = {
            "payoutReference", "adminNotes", "tierNameSnapshot", "voidReason"
    };

    private final CommissionRepository repository;
    private final CommissionMapper mapper;
    private final CommissionPeriodSummaryRepository periodSummaryRepository;
    private final PromoterRepository promoterRepository;
    private final CurrencyRepository currencyRepository;
    private final ConversionEnricher conversionEnricher;
    private final DefaultSortResolver defaultSortResolver;
    private final CommissionAuditRecorder auditRecorder;

    // ─── Read ───────────────────────────────────────────────────────────────

    public CommissionDto get(UUID uuid) {
        Commission commission = findManaged(uuid);
        return enrich(mapper.toDto(commission), commission);
    }

    /**
     * Once the commission has been paid, freezes {@code amountConverted} to
     * the persisted paid-time snapshot ({@code exchangeRateAtPaid}) — never
     * recomputed against today's rate — and, when both snapshots are
     * present, exposes the FX variance the company absorbed between devengo
     * and payout. A still-PENDING/APPROVED row falls back to a live
     * conversion (ADR 0015 §6 Caso B).
     */
    private CommissionDto enrich(CommissionDto dto, Commission commission) {
        if (commission.getExchangeRateAtPaid() != null) {
            int scale = commission.getCurrency() != null && commission.getCurrency().getDecimalPlaces() != null
                    ? commission.getCurrency().getDecimalPlaces().intValue() : 2;
            String officialCode = conversionEnricher.officialCurrencyCode();
            BigDecimal amountAtPaid = commission.getAmount()
                    .multiply(commission.getExchangeRateAtPaid())
                    .setScale(scale, RoundingMode.HALF_UP);
            BigDecimal fxVariance = null;
            if (commission.getExchangeRateAtEarned() != null) {
                BigDecimal amountAtEarned = commission.getAmount()
                        .multiply(commission.getExchangeRateAtEarned())
                        .setScale(scale, RoundingMode.HALF_UP);
                fxVariance = amountAtPaid.subtract(amountAtEarned);
            }
            return dto.withConversion(amountAtPaid, officialCode, commission.getExchangeRateAtPaid(),
                    commission.getPaidRateDate(), fxVariance);
        }
        var conv = conversionEnricher.toOfficial(commission.getAmount(), commission.getCurrency());
        return dto.withConversion(conv.amountConverted(), conv.currencyCode(), conv.rate(), conv.rateDate(), null);
    }

    /** Monthly commission history for one promoter, most recent period first —
     *  {@code GET /v1/admin/promoters/{uuid}/commissions/summary}. */
    public List<CommissionPeriodSummaryDto> periodSummaryFor(UUID promoterUuid) {
        Promoter promoter = promoterRepository.findByUuid(promoterUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        return periodSummaryRepository.findByPromoterIdOrderByPeriodStartDesc(promoter.getId())
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    private CommissionPeriodSummaryDto toSummaryDto(CommissionPeriodSummary s) {
        String currencyCode = s.getCurrencyId() != null
                ? currencyRepository.findById(s.getCurrencyId()).map(c -> c.getCode()).orElse(null)
                : null;
        return new CommissionPeriodSummaryDto(
                s.getPeriodStrategy(), s.getPeriodStart(), s.getPeriodEnd(),
                s.getCommissionCount(), s.getTotalAmount(), currencyCode);
    }

    /**
     * Admin queue list. Default sort {@code earnedAt DESC} matches the V26
     * composite index {@code idx_commissions_member_earned} when filtered
     * by member; the {@code (promoter_id, status, period_start)} index
     * backs filters by promoter + status + period (the canonical
     * liquidation path).
     */
    public Page<CommissionDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "commission", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "commission");
        Specification<Commission> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "commission.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(c -> enrich(mapper.toDto(c), c));
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("commission", pageable);
    }

    // ─── Write (the one mutation this admin-read service owns) ─────────────

    /**
     * Voids a single PENDING commission — e.g. a promoter's non-compliance
     * discovered before the period closes. Excludes it from the next payout
     * ({@code findPendingForPeriod}'s {@code status = 'PENDING'} predicate)
     * without touching the rest of that promoter's period, unlike the
     * all-or-nothing {@code POST /payout} range. Only PENDING is eligible:
     * PAID is settled money (no retroactive rewrite, same rule
     * {@code CommissionReRatingService} follows); VOIDED/DISPUTED are
     * already terminal.
     */
    @Transactional
    public CommissionDto voidCommission(UUID uuid, String reason) {
        Commission commission = findManaged(uuid);
        if (!Commission.CommissionStatus.PENDING.name().equals(commission.getStatus())) {
            throw new IllegalArgumentException("commission.void.not_pending");
        }
        Map<String, Object> before = auditRecorder.snapshot(commission);
        commission.setStatus(Commission.CommissionStatus.VOIDED.name());
        commission.setVoidedAt(Instant.now());
        commission.setVoidReason(reason);
        auditRecorder.recordUpdate(uuid, before, auditRecorder.snapshot(commission));
        return enrich(mapper.toDto(commission), commission);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Commission findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("commission.not_found"));
    }

    private static Specification<Commission> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
