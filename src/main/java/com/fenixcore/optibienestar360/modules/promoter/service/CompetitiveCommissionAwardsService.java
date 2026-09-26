package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveAwardSettlementDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardSettlementRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Pay/void over {@link CompetitiveCommissionAwardSettlement} cuts — never the award's {@code
 * amount} directly, since what's actually owed at any moment is the sum of its settlements (hub
 * plan competitive-commission-rules, Fase 2b). The rate-snapshot-at-pay pattern mirrors the
 * legacy leaderboard's own pay logic exactly (same official-currency conversion, same graceful
 * no-rate-available fallback) — retired in Fase 3, migrated into this same model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitiveCommissionAwardsService {

    private final CompetitiveCommissionAwardRepository awardRepository;
    private final CompetitiveCommissionAwardSettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final OrganizationRepository organizationRepository;
    private final CurrencyConversionService conversionService;

    @Transactional(readOnly = true)
    public Page<CompetitiveAwardDto> list(Pageable pageable, String filter) {
        Specification<CompetitiveCommissionAward> spec = (root, query, cb) -> cb.conjunction();
        if (filter != null && !filter.isBlank()) {
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        return awardRepository.findAll(spec, pageable).map(CompetitiveAwardDto::from);
    }

    @Transactional(readOnly = true)
    public CompetitiveAwardDto get(UUID uuid) {
        return CompetitiveAwardDto.from(awardRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_commission_award.not_found")));
    }

    @Transactional(readOnly = true)
    public List<CompetitiveAwardSettlementDto> settlements(UUID awardUuid) {
        return settlementRepository.findByAward_UuidOrderByCutSequence(awardUuid).stream()
                .map(CompetitiveAwardSettlementDto::from).toList();
    }

    /** Pays one settlement cut; marks the award {@code PAID} once its settlements cover the full {@code amount}. */
    @Auditable(entity = "competitive_commission_award_settlement", action = AuditAction.UPDATE, uuidArgIndex = 0)
    @Transactional
    public void paySettlement(UUID settlementUuid, String payoutReference, UUID payoutPaymentUuid) {
        CompetitiveCommissionAwardSettlement settlement = settlementRepository.findByUuid(settlementUuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_commission_award_settlement.not_found"));
        if (!"PENDING".equals(settlement.getStatus())) {
            throw new IllegalArgumentException("competitive_commission_award_settlement.pay.not_pending");
        }
        Instant now = Instant.now();
        settlement.setStatus("PAID");
        settlement.setPaidAt(now);
        settlement.setPayoutReference(payoutReference);
        if (payoutPaymentUuid != null) {
            paymentRepository.findByUuid(payoutPaymentUuid).ifPresent(settlement::setPayoutPayment);
        }
        snapshotRate(settlement.getAmount(), settlement.getCurrency(), now, settlement);
        markAwardPaidIfFullyCovered(settlement.getAward());
    }

    /** Shortcut: pays every PENDING settlement of the award (§5 — the same reference/payment applies to each). */
    @Auditable(entity = "competitive_commission_award", action = AuditAction.UPDATE, uuidArgIndex = 0)
    @Transactional
    public int payAward(UUID awardUuid, String payoutReference, UUID payoutPaymentUuid) {
        CompetitiveCommissionAward award = awardRepository.findByUuid(awardUuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_commission_award.not_found"));
        List<CompetitiveCommissionAwardSettlement> pending = settlementRepository
                .findByAward_IdAndActiveTrueAndStatus(award.getId(), "PENDING");
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("competitive_commission_award.pay.no_pending_settlements");
        }
        for (CompetitiveCommissionAwardSettlement settlement : pending) {
            paySettlement(settlement.getUuid(), payoutReference, payoutPaymentUuid);
        }
        return pending.size();
    }

    /** Voids the award and any of its still-PENDING settlements — a PAID one is never touched. */
    @Auditable(entity = "competitive_commission_award", action = AuditAction.DELETE, uuidArgIndex = 0)
    @Transactional
    public void voidAward(UUID awardUuid, String reason) {
        CompetitiveCommissionAward award = awardRepository.findByUuid(awardUuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_commission_award.not_found"));
        if ("PAID".equals(award.getStatus())) {
            throw new IllegalArgumentException("competitive_award.already_paid");
        }
        Instant now = Instant.now();
        award.setStatus("VOIDED");
        award.setVoidedAt(now);
        award.setVoidReason(reason);
        for (CompetitiveCommissionAwardSettlement settlement : settlementRepository
                .findByAward_IdAndActiveTrueAndStatus(award.getId(), "PENDING")) {
            settlement.setStatus("VOIDED");
            settlement.setVoidedAt(now);
            settlement.setVoidReason(reason);
        }
    }

    private void markAwardPaidIfFullyCovered(CompetitiveCommissionAward award) {
        if (award == null || "PAID".equals(award.getStatus())) {
            return;
        }
        BigDecimal totalPaid = settlementRepository.findByAward_IdAndActiveTrueAndStatus(award.getId(), "PAID").stream()
                .map(CompetitiveCommissionAwardSettlement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPaid.compareTo(award.getAmount()) >= 0) {
            award.setStatus("PAID");
            award.setPaidAt(Instant.now());
        }
    }

    private void snapshotRate(BigDecimal amount, Currency currency, Instant now, CompetitiveCommissionAwardSettlement settlement) {
        Currency official = organizationRepository.findSingleton().getOfficialCurrency();
        if (official.getId().equals(currency.getId())) {
            return;
        }
        try {
            var result = conversionService.convert(amount, currency, official, now);
            settlement.setExchangeRateAtPaid(result.rate());
            settlement.setPaidRateDate(result.rateDate());
        } catch (NoExchangeRateAvailableException noRate) {
            log.info("No exchange rate available to snapshot for competitive commission award settlement {}; leaving null",
                    settlement.getUuid());
        }
    }
}
