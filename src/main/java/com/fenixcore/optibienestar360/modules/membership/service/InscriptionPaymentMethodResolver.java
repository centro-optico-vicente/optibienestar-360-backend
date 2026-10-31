package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives the "Modalidad de pago" of the B2C dossier (hub ADR 0031 §14): the
 * payment method of the first approved inscription payment line of a
 * membership. Read-only; nothing is stored.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InscriptionPaymentMethodResolver {

    private final PaymentLineRepository paymentLineRepository;

    /** {@code null} while the membership has no approved inscription payment. */
    public DisplayRef resolve(Membership membership) {
        if (membership == null || membership.getId() == null) {
            return null;
        }
        return paymentLineRepository.findInscriptionLines(membership.getId(), PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(line -> DisplayRefs.ref(line.getPaymentType()))
                .orElse(null);
    }
}
