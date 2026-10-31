package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimResolveRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCreditClaim;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCreditClaim.ClaimStatus;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCreditClaimRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PersonCreditEntryRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Claims on the remaining credit of an approved payment (V205). The payer (or the beneficiary whose credit it is)
 * opens one on their own payment; staff may open one for somebody else and resolve any. While a claim is OPEN its
 * amount is frozen for the automatic application; staff can still assign it by hand, which closes the claim as
 * {@code USED_BY_AGREEMENT} (the agreement with the person is what gives the green light to use the funds).
 *
 * <p>Depends on repositories only, so {@code PaymentsService} can use it without a cycle.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCreditClaimService {

    private final PaymentCreditClaimRepository repository;
    private final PaymentRepository paymentRepository;
    private final PersonCreditEntryRepository creditRepository;
    private final PersonCreditService personCreditService;
    private final UserRepository userRepository;

    // ─── Reads ──────────────────────────────────────────────────────────────

    public Page<PaymentClaimDto> list(String status, Pageable pageable) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        Page<PaymentCreditClaim> page = normalized == null
                ? repository.findByActiveTrue(pageable)
                : repository.findByActiveTrueAndClaimStatus(normalized, pageable);
        return page.map(PaymentCreditClaimService::toDto);
    }

    public Optional<PaymentClaimDto> openClaimOf(Payment payment) {
        if (payment.getId() == null) return Optional.empty();
        return repository.findOpenByPaymentId(payment.getId()).map(PaymentCreditClaimService::toDto);
    }

    /** Amount of the payment frozen by its OPEN claim, capped at what is left. */
    public BigDecimal frozenAmount(Payment payment) {
        BigDecimal frozen = repository.frozenAmountOfPayment(payment.getId());
        BigDecimal remaining = creditRepository.remainingOfPayment(payment.getId());
        return frozen.min(remaining.max(BigDecimal.ZERO));
    }

    /** What the automatic application may use of a payment: remaining credit minus the frozen amount, never negative. */
    public BigDecimal usableCredit(Payment payment) {
        BigDecimal remaining = creditRepository.remainingOfPayment(payment.getId());
        return remaining.subtract(frozenAmount(payment)).max(BigDecimal.ZERO);
    }

    // ─── Open / withdraw ────────────────────────────────────────────────────

    /** The caller opens a claim on their own payment (payer or beneficiary). */
    @Transactional
    @Auditable(entity = "payment_credit_claim", action = AuditAction.CREATE)
    public PaymentClaimDto openOwn(UUID actorUserUuid, UUID paymentUuid, PaymentClaimRequest request) {
        User actor = userOf(actorUserUuid);
        Payment payment = paymentOf(paymentUuid);
        if (!isOwner(payment, actor)) {
            throw new NoSuchElementException("payment.not_found");
        }
        return toDto(open(payment, actor, request));
    }

    /** Staff open a claim on somebody else's payment. */
    @Transactional
    @Auditable(entity = "payment_credit_claim", action = AuditAction.CREATE)
    public PaymentClaimDto openFor(UUID actorUserUuid, UUID paymentUuid, PaymentClaimRequest request) {
        return toDto(open(paymentOf(paymentUuid), userOf(actorUserUuid), request));
    }

    private PaymentCreditClaim open(Payment payment, User actor, PaymentClaimRequest request) {
        if (!"IN".equals(payment.getDirection()) || !Payment.PaymentStatus.APPROVED.name().equals(payment.getStatus())) {
            throw new IllegalArgumentException("payment.claim.not_claimable");
        }
        if (repository.findOpenByPaymentId(payment.getId()).isPresent()) {
            throw new IllegalArgumentException("payment.claim.already_open");
        }
        BigDecimal remaining = creditRepository.remainingOfPayment(payment.getId());
        if (request.claimedAmount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("payment.claim.amount_exceeds_credit");
        }
        PaymentCreditClaim claim = new PaymentCreditClaim();
        claim.setPayment(payment);
        claim.setReason(request.reason());
        claim.setClaimedAmount(request.claimedAmount());
        claim.setComment(request.comment());
        claim.setOpenedBy(actor);
        return repository.save(claim);
    }

    /** The caller withdraws their own OPEN claim. */
    @Transactional
    @Auditable(entity = "payment_credit_claim", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public void withdrawOwn(UUID actorUserUuid, UUID paymentUuid) {
        User actor = userOf(actorUserUuid);
        Payment payment = paymentOf(paymentUuid);
        if (!isOwner(payment, actor)) {
            throw new NoSuchElementException("payment.not_found");
        }
        PaymentCreditClaim claim = repository.findOpenByPaymentId(payment.getId())
                .orElseThrow(() -> new NoSuchElementException("payment.claim.not_found"));
        close(claim, ClaimStatus.WITHDRAWN, actor, null);
    }

    // ─── Resolve ────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "payment_credit_claim", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PaymentClaimDto resolve(UUID claimUuid, PaymentClaimResolveRequest request, UUID actorUserUuid) {
        PaymentCreditClaim claim = repository.findByUuid(claimUuid)
                .orElseThrow(() -> new NoSuchElementException("payment.claim.not_found"));
        if (!ClaimStatus.OPEN.name().equals(claim.getClaimStatus())) {
            throw new IllegalArgumentException("payment.claim.not_open");
        }
        User actor = userOf(actorUserUuid);
        switch (request.action()) {
            case "KEEP_AS_CREDIT" -> close(claim, ClaimStatus.KEPT_AS_CREDIT, actor, request.note());
            case "REJECT" -> close(claim, ClaimStatus.REJECTED, actor, request.note());
            case "REFUND" -> {
                BigDecimal refunded = request.refundedAmount();
                BigDecimal remaining = creditRepository.remainingOfPayment(claim.getPayment().getId());
                if (refunded == null || refunded.signum() <= 0
                        || refunded.compareTo(claim.getClaimedAmount()) > 0 || refunded.compareTo(remaining) > 0) {
                    throw new IllegalArgumentException("payment.claim.refund_amount_invalid");
                }
                personCreditService.recordReversal(claim.getPayment(), refunded,
                        request.note() != null && !request.note().isBlank() ? request.note() : "Refund agreed on a claim");
                claim.setRefundedAmount(refunded);
                close(claim, ClaimStatus.REFUNDED, actor, request.note());
            }
            default -> throw new IllegalArgumentException("payment.claim.action.invalid");
        }
        return toDto(claim);
    }

    /**
     * Staff assigned the payment's credit by hand: the claim is closed as used by agreement. A no-op when the
     * payment has no OPEN claim.
     */
    @Transactional
    public void closeAsUsedByAgreement(Payment payment, UUID actorUserUuid) {
        repository.findOpenByPaymentId(payment.getId()).ifPresent(claim ->
                close(claim, ClaimStatus.USED_BY_AGREEMENT,
                        actorUserUuid == null ? null : userRepository.findByUuid(actorUserUuid).orElse(null),
                        "Assigned by hand by agreement with the person"));
    }

    private void close(PaymentCreditClaim claim, ClaimStatus status, User actor, String note) {
        claim.setClaimStatus(status.name());
        claim.setResolvedBy(actor);
        claim.setResolvedAt(Instant.now());
        claim.setResolutionNote(note);
        repository.save(claim);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private User userOf(UUID userUuid) {
        return userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
    }

    private Payment paymentOf(UUID paymentUuid) {
        return paymentRepository.findByUuid(paymentUuid)
                .filter(Payment::isActive)
                .orElseThrow(() -> new NoSuchElementException("payment.not_found"));
    }

    /** The payer, or the beneficiary whose credit it is. */
    private static boolean isOwner(Payment payment, User actor) {
        Person me = actor.getPerson();
        if (me == null) return false;
        boolean payer = payment.getPerson() != null && payment.getPerson().getId().equals(me.getId());
        boolean beneficiary = payment.getBeneficiaryPerson() != null
                && payment.getBeneficiaryPerson().getId().equals(me.getId());
        return payer || beneficiary;
    }

    static PaymentClaimDto toDto(PaymentCreditClaim c) {
        Payment p = c.getPayment();
        return new PaymentClaimDto(c.getUuid(), p.getUuid(), DisplayRefs.ref(p), DisplayRefs.ref(p.getPerson()),
                p.getBeneficiaryPerson() == null ? null : DisplayRefs.ref(p.getBeneficiaryPerson()),
                c.getReason(), c.getClaimedAmount(), p.getCurrency().getCode(), c.getComment(), c.getClaimStatus(),
                c.getOpenedAt(), c.getOpenedBy() == null ? null : DisplayRefs.ref(c.getOpenedBy()),
                c.getResolvedAt(), c.getResolvedBy() == null ? null : DisplayRefs.ref(c.getResolvedBy()),
                c.getResolutionNote(), c.getRefundedAmount());
    }
}
