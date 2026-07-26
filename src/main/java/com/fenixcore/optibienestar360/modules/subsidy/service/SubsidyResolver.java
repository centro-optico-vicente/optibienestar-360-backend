package com.fenixcore.optibienestar360.modules.subsidy.service;

import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyBeneficiary;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyBeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only view of active subsidies, consumed by other verticals — the solvency
 * engine ({@code MembershipStatusService}), the beneficiary inscription biller
 * ({@code BeneficiariesService}), and receipt/amount computation.
 *
 * <p>Exposes only percentages / booleans (no fee arithmetic beyond the
 * convenience {@link #netMonthlyFee}) so it depends on nothing but the subsidy
 * repositories — the dependency direction stays one-way (membership / member /
 * payment → subsidy), no bean cycle.</p>
 *
 * <p>Resolution rule: when a member has more than one active subsidy on a given
 * date, the <b>highest</b> percentage wins for each fee — subsidies never
 * accumulate (vertical-12 business rule).</p>
 *
 * <p>No class-level {@code @Transactional}: every method is a read delegated to
 * repositories that already run read-only transactions, plus in-memory
 * computation — so this bean needs no proxy of its own.</p>
 */
@Service
@RequiredArgsConstructor
public class SubsidyResolver {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SubsidyRepository repository;
    private final SubsidyBeneficiaryRepository beneficiaryRepository;

    /**
     * True when {@code memberId} has an active subsidy on {@code on} that fully
     * exonerates the monthly fee ({@code monthly_percentage >= 100}). Drives the
     * solvency short-circuit: a fully-exonerated membership stays ACTIVE with no
     * payment row (vertical-5 #1 / PDF #1.b).
     */
    public boolean fullMonthlyExoneration(Long memberId, LocalDate on) {
        return repository.findActiveForMember(memberId, on).stream()
                .map(Subsidy::getMonthlyPercentage)
                .filter(Objects::nonNull)
                .anyMatch(p -> p.compareTo(HUNDRED) >= 0);
    }

    /** The best (highest) active monthly subsidy percentage for the member on {@code on}. */
    public Optional<BigDecimal> monthlyPercentage(Long memberId, LocalDate on) {
        return repository.findActiveForMember(memberId, on).stream()
                .map(Subsidy::getMonthlyPercentage)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
    }

    /**
     * Applies the best active monthly subsidy to a gross monthly fee.
     * {@code gross * (1 - pct/100)}, half-up to 2 decimals. Returns the gross
     * unchanged when there is no active monthly subsidy (partial subsidy still
     * owes the reduced amount — vertical-5 #2).
     */
    public BigDecimal netMonthlyFee(BigDecimal grossMonthly, Long memberId, LocalDate on) {
        if (grossMonthly == null) return null;
        return monthlyPercentage(memberId, on)
                .map(pct -> grossMonthly
                        .multiply(BigDecimal.ONE.subtract(pct.movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP))
                .orElse(grossMonthly);
    }

    /**
     * True when a specific beneficiary's extra inscription is fully exonerated by
     * an active subsidy on {@code on}. Read at beneficiary reactivation time to
     * skip re-charging an already-exonerated beneficiary.
     */
    public boolean beneficiaryInscriptionExonerated(Long beneficiaryId, LocalDate on) {
        return beneficiaryRepository.findActiveForBeneficiary(beneficiaryId, on).stream()
                .map(SubsidyBeneficiary::getInscriptionPercentage)
                .filter(Objects::nonNull)
                .anyMatch(p -> p.compareTo(HUNDRED) >= 0);
    }

    /**
     * Batch variant of {@link #fullMonthlyExoneration} for the daily status
     * sweep — one query for all candidate members (vertical-5 #3, no N+1).
     */
    public Set<Long> memberIdsWithFullMonthlyExoneration(Collection<Long> memberIds, LocalDate on) {
        if (memberIds == null || memberIds.isEmpty()) return Set.of();
        return repository.memberIdsWithFullMonthlyExoneration(memberIds, on);
    }
}
