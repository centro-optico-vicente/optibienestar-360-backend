package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Creates the one-time "afiliado adicional" inscription charge (v2 flyer,
 * "Afiliado Adicional $5") when a beneficiary is added beyond the plan's
 * {@code included_beneficiaries} cap, and resolves a payment id back to its
 * external UUID for output DTOs.
 *
 * <p>Lives in the payment module and is injected into
 * {@code BeneficiariesService} so the member module keeps referring to the
 * payment only by its opaque {@code Long} id (the {@code Beneficiary} entity
 * deliberately avoids a {@code @ManyToOne} to {@code Payment}), while the
 * actual Payment row is built here where the payment invariants live.</p>
 *
 * <p>The charge is registered as an {@code inscription} payment in status
 * {@code PENDING} with method {@code OTHER} as a placeholder — the admin
 * records the real method / proof and approves it through the standard payment
 * review flow, at which point the beneficiary's {@code extra_inscription_paid}
 * flag is flipped (follow-up hook on approval; today the admin toggles it via
 * the beneficiary update endpoint).</p>
 */
@Component
@RequiredArgsConstructor
public class BeneficiaryInscriptionBiller {

    private final PaymentRepository paymentRepository;
    private final CurrencyRepository currencyRepository;

    /**
     * Registers a PENDING extra-beneficiary inscription payment against the
     * titular's active membership and returns its id (to link from the
     * beneficiary row).
     */
    public Long chargeExtraInscription(Membership membership, BigDecimal fee) {
        Payment payment = new Payment();
        payment.setMembership(membership);
        payment.setAmount(fee);
        // plan pricing is USD (ADR 0008)
        payment.setCurrency(currencyRepository.findByCode("USD")
                .orElseThrow(() -> new NoSuchElementException("currency.not_found")));
        payment.setPaymentMethod(Payment.PaymentMethod.OTHER);
        payment.setPaymentDate(LocalDate.now());
        payment.setInscription(true);   // V23 CHECK: inscription rows carry no applied_period
        payment.setStatus(Payment.PaymentStatus.PENDING.name());
        payment.setAdminNotes("Inscripción de afiliado adicional (excede beneficiarios incluidos del plan)");
        return paymentRepository.save(payment).getId();
    }

    /** Opaque id → external UUID for output DTOs; {@code null}-safe. */
    public UUID resolveUuid(Long paymentId) {
        if (paymentId == null) return null;
        return paymentRepository.findById(paymentId).map(Payment::getUuid).orElse(null);
    }
}
