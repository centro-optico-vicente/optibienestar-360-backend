package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService.ConversionResult;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.ChargeStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PersonCreditEntryRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies a person's credit ("saldo a favor") to the monthly charges of their membership automatically (V205,
 * hub ADR 0029): when the membership is created and every day by the {@code CREDIT_AUTO_APPLY} job.
 *
 * <p>Rules: full months only — a month is covered when one payment's usable credit reaches its net charge, never
 * partly; payments are consumed oldest first; the usable credit of a payment is what is left minus the amount
 * frozen by its OPEN claim; nothing is applied while the person's credit is on hold
 * ({@code Person.creditAutoApply = false}). A charge in another currency than the credit is converted at the
 * rate of the credit payment's date, which each applied row records. The first application of a payment attaches it
 * to the membership and triggers its single commission; later applications of the same payment add no commission.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CreditApplicationService {

    /** Hard stop on months covered in one run — a safety net, far above any real advance. */
    private static final int MAX_MONTHS = 120;

    private final PersonCreditEntryRepository creditRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final PaymentCreditClaimService claimService;
    private final MembershipChargeService chargeService;
    private final PersonCreditService personCreditService;
    private final PaymentsService paymentsService;
    private final CurrencyConversionService conversionService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Applies the credit of {@code membership}'s person to its next uncovered months.
     *
     * @return the number of months covered
     */
    @Transactional
    public int applyCredit(Membership membership) {
        Person owner = membership.getMember().getPerson();
        if (owner == null || !owner.isCreditAutoApply()
                || !Membership.LifecycleStatus.ACTIVE.name().equals(membership.getStatus()) || !membership.isActive()) {
            return 0;
        }
        int coveredMonths = 0;
        LocalDate period = MembershipChargeService.nextChargeablePeriod(membership);

        for (Object[] row : creditRepository.paymentsWithCredit(owner.getId())) {
            Payment payment = paymentRepository.findById((Long) row[0]).orElse(null);
            if (payment == null) {
                continue;
            }
            BigDecimal usable = claimService.usableCredit(payment);
            LocalDate firstCovered = null;
            LocalDate lastCovered = null;

            for (int guard = 0; guard < MAX_MONTHS && usable.signum() > 0; guard++) {
                MembershipCharge charge = chargeService.ensureChargeForPeriod(membership, period);
                if (ChargeStatus.COVERED.name().equals(charge.getStatus())
                        || ChargeStatus.WAIVED.name().equals(charge.getStatus())) {
                    period = period.plusMonths(1);   // nothing to pay for this month
                    continue;
                }
                ConversionResult conversion;
                try {
                    conversion = conversionService.convert(charge.getAmount(), charge.getCurrency(),
                            payment.getCurrency(), payment.getPaymentDate());
                } catch (NoExchangeRateAvailableException noRate) {
                    log.warn("Credit of payment {} left for manual assignment: {}", payment.getUuid(), noRate.getMessage());
                    break;
                }
                BigDecimal cost = conversion.convertedAmount();
                if (usable.compareTo(cost) < 0) {
                    break;   // full months only
                }
                chargeService.coverWithCredit(membership, period, payment);
                personCreditService.applyForCharge(payment, membership, charge, cost, conversion.rate(), conversion.rateDate());
                usable = usable.subtract(cost);
                if (firstCovered == null) {
                    firstCovered = period;
                }
                lastCovered = period;
                period = period.plusMonths(1);
                coveredMonths++;
            }

            if (firstCovered != null && payment.getMembership() == null) {
                LocalDate through = lastCovered.equals(firstCovered) ? null : lastCovered;
                paymentsService.attachCreditPaymentToMembership(payment, membership, firstCovered, through, false);
            }
        }
        return coveredMonths;
    }

    /**
     * {@link #applyCredit} for one membership in its own transaction, never throwing: a failure here must not
     * undo an enrollment or stop the rest of the daily run. Runs after the caller's commit when called from one.
     *
     * @return the number of months covered (0 on failure)
     */
    public int applyCreditSafely(Long membershipId) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Integer covered = template.execute(status ->
                    membershipRepository.findById(membershipId).map(this::applyCredit).orElse(0));
            return covered == null ? 0 : covered;
        } catch (RuntimeException ex) {
            log.error("Automatic credit application failed for membership {}", membershipId, ex);
            return 0;
        }
    }

    /** Daily run: applies credit to every ACTIVE membership whose person holds some. */
    public BatchResult applyForAllPending() {
        Set<Long> personIds = new LinkedHashSet<>();
        for (Object personId : creditRepository.personIdsWithCredit()) {
            personIds.add((Long) personId);
        }
        List<Long> membershipIds = new ArrayList<>();
        for (Long personId : personIds) {
            membershipRepository.findActiveByPersonId(personId).forEach(m -> membershipIds.add(m.getId()));
        }
        int months = 0;
        for (Long membershipId : membershipIds) {
            months += applyCreditSafely(membershipId);
        }
        log.info("CREDIT_AUTO_APPLY: people={} memberships={} monthsCovered={}", personIds.size(), membershipIds.size(), months);
        return new BatchResult(personIds.size(), membershipIds.size(), months);
    }

    /** Summary of {@link #applyForAllPending()}. */
    public record BatchResult(int people, int memberships, int monthsCovered) {}
}
