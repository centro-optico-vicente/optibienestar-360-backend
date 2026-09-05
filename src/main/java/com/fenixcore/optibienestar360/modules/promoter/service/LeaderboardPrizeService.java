package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardPrizeAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardPrizeDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardPrizeRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PrizeAwardResult;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrize;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrizeAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrizeAward.AwardStatus;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.LeaderboardPrizeAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.LeaderboardPrizeRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardService.RankedEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leaderboard prize configuration + period-close awarding (v2 PDF #5, V42).
 *
 * <p>Config: admin CRUD over {@link LeaderboardPrize} (prize per rank per
 * strategy). Awarding: at period close, ranks promoters via
 * {@link LeaderboardService#rank} and grants each configured prize to the
 * matching rank — idempotent per (promoter, period, rank) via the V42 partial
 * UNIQUE, so re-runs are no-ops.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LeaderboardPrizeService {

    private static final String DEFAULT_CURRENCY = "USD";

    private final LeaderboardPrizeRepository prizeRepository;
    private final LeaderboardPrizeAwardRepository awardRepository;
    private final LeaderboardService leaderboardService;
    private final CurrencyRepository currencyRepository;
    private final ConversionEnricher conversionEnricher;
    private final CurrencyConversionService conversionService;
    private final OrganizationRepository organizationRepository;

    // ─── Config CRUD ──────────────────────────────────────────────────────────

    public LeaderboardPrizeDto get(UUID uuid) {
        return LeaderboardPrizeDto.from(findManaged(uuid));
    }

    public List<LeaderboardPrizeDto> list() {
        return prizeRepository.findByActiveTrue().stream()
                .map(LeaderboardPrizeDto::from)
                .toList();
    }

    @Transactional
    public LeaderboardPrizeDto create(LeaderboardPrizeRequest req) {
        if (prizeRepository.existsByRankAndPeriodStrategyAndActiveTrue(req.rank(), req.periodStrategy())) {
            throw new IllegalArgumentException("leaderboard_prize.duplicate");
        }
        LeaderboardPrize prize = new LeaderboardPrize();
        prize.setRank(req.rank());
        prize.setPeriodStrategy(req.periodStrategy());
        prize.setPrizeAmount(req.prizeAmount());
        prize.setPrizeCurrency(resolveCurrency(req.prizeCurrency() != null ? req.prizeCurrency() : DEFAULT_CURRENCY));
        return LeaderboardPrizeDto.from(prizeRepository.save(prize));
    }

    @Transactional
    public LeaderboardPrizeDto update(UUID uuid, LeaderboardPrizeRequest req) {
        LeaderboardPrize prize = findManaged(uuid);
        boolean keyChanged = prize.getRank() != req.rank() || prize.getPeriodStrategy() != req.periodStrategy();
        if (keyChanged && prizeRepository.existsByRankAndPeriodStrategyAndActiveTrue(req.rank(), req.periodStrategy())) {
            throw new IllegalArgumentException("leaderboard_prize.duplicate");
        }
        prize.setRank(req.rank());
        prize.setPeriodStrategy(req.periodStrategy());
        prize.setPrizeAmount(req.prizeAmount());
        if (req.prizeCurrency() != null) prize.setPrizeCurrency(resolveCurrency(req.prizeCurrency()));
        return LeaderboardPrizeDto.from(prize);   // managed → dirty-check
    }

    @Transactional
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    // ─── Awarding ─────────────────────────────────────────────────────────────

    /**
     * Awards the configured prizes for the {@code strategy} period containing
     * {@code reference}. Idempotent. {@code dryRun} computes without persisting.
     */
    @Transactional
    public PrizeAwardResult award(PeriodStrategy strategy, LocalDate reference, boolean dryRun) {
        PeriodStrategies.Window window = PeriodStrategies.window(strategy.name(), reference);

        Map<Integer, LeaderboardPrize> prizesByRank = prizeRepository
                .findByPeriodStrategyAndActiveTrue(strategy).stream()
                .collect(Collectors.toMap(LeaderboardPrize::getRank, Function.identity(), (a, b) -> a));
        if (prizesByRank.isEmpty()) {
            return new PrizeAwardResult(strategy, window.start(), window.end(), dryRun, 0, BigDecimal.ZERO, DEFAULT_CURRENCY);
        }

        int maxRank = prizesByRank.keySet().stream().max(Integer::compareTo).orElse(0);
        List<RankedEntry> ranked = leaderboardService.rank(strategy, window, maxRank);

        int created = 0;
        BigDecimal total = BigDecimal.ZERO;
        String currency = DEFAULT_CURRENCY;
        for (RankedEntry e : ranked) {
            LeaderboardPrize prize = prizesByRank.get(e.rank());
            if (prize == null) {
                continue;
            }
            boolean already = awardRepository
                    .existsByPromoterIdAndPeriodStrategyAndPeriodStartAndPeriodEndAndRankAndActiveTrue(
                            e.promoter().getId(), strategy, window.start(), window.end(), e.rank());
            if (already) {
                continue;
            }
            if (!dryRun) {
                LeaderboardPrizeAward award = new LeaderboardPrizeAward();
                award.setPromoter(e.promoter());
                award.setPeriodStrategy(strategy);
                award.setPeriodStart(window.start());
                award.setPeriodEnd(window.end());
                award.setRank(e.rank());
                award.setMetricAmount(e.totalCommission());
                award.setPrizeAmount(prize.getPrizeAmount());
                award.setPrizeCurrency(prize.getPrizeCurrency());
                award.setStatus(AwardStatus.PENDING.name());
                awardRepository.save(award);
            }
            created++;
            total = total.add(prize.getPrizeAmount());
            currency = prize.getPrizeCurrency().getCode();
        }
        return new PrizeAwardResult(strategy, window.start(), window.end(), dryRun, created, total, currency);
    }

    /**
     * Runner entry point: for every strategy that has active prizes, award the
     * period that <b>just closed</b> as of {@code runDate} (its last day was
     * yesterday). Strategies whose period has not fully closed are skipped, so
     * the monthly job never awards a half-finished period.
     */
    @Transactional
    public List<PrizeAwardResult> awardClosedPeriods(LocalDate runDate, boolean dryRun) {
        LocalDate yesterday = runDate.minusDays(1);
        List<PeriodStrategy> strategies = prizeRepository.findByActiveTrue().stream()
                .map(LeaderboardPrize::getPeriodStrategy)
                .distinct()
                .toList();

        List<PrizeAwardResult> results = new ArrayList<>();
        for (PeriodStrategy strategy : strategies) {
            PeriodStrategies.Window w = PeriodStrategies.window(strategy.name(), yesterday);
            if (!w.end().equals(yesterday)) {
                continue;   // period not fully closed as of the run date
            }
            results.add(award(strategy, yesterday, dryRun));
        }
        return results;
    }

    // ─── Payout ───────────────────────────────────────────────────────────────

    /**
     * Marks a granted prize award as PAID — leaderboard_prize_awards had no
     * payout timestamp at all until V92 (only {@code awarded_at}, the
     * period-close moment). Mirrors {@code BonusAwardsService.pay}: stamps
     * {@code paidAt}, then snapshots the exchange rate to the organization's
     * official currency as of now (ADR 0015 §5/§7 Caso A), degrading to a
     * null snapshot rather than blocking the payout when no rate is vigente.
     *
     * @throws IllegalArgumentException (422) when the award is already PAID or VOIDED
     */
    @Transactional
    public LeaderboardPrizeAwardDto pay(UUID uuid, String payoutReference) {
        LeaderboardPrizeAward award = findManagedAward(uuid);
        if (!AwardStatus.PENDING.name().equals(award.getStatus())) {
            throw new IllegalArgumentException("leaderboard_prize_award.pay.not_pending");
        }

        Instant now = Instant.now();
        award.setStatus(AwardStatus.PAID.name());
        award.setPaidAt(now);
        award.setPayoutReference(payoutReference);

        var official = organizationRepository.findSingleton().getOfficialCurrency();
        if (!official.getId().equals(award.getPrizeCurrency().getId())) {
            try {
                var result = conversionService.convert(award.getPrizeAmount(), award.getPrizeCurrency(), official, now);
                award.setExchangeRateUsed(result.rate());
                award.setExchangeRateDate(result.rateDate());
            } catch (NoExchangeRateAvailableException noRate) {
                log.info("No exchange rate available to snapshot for leaderboard prize award {}; leaving null", uuid);
            }
        }

        return LeaderboardPrizeAwardDto.from(award, conversionEnricher);
    }

    private LeaderboardPrizeAward findManagedAward(UUID uuid) {
        return awardRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("leaderboard_prize_award.not_found"));
    }

    private LeaderboardPrize findManaged(UUID uuid) {
        return prizeRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("leaderboard_prize.not_found"));
    }

    private Currency resolveCurrency(String code) {
        return currencyRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }
}
