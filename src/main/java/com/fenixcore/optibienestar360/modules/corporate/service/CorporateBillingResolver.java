package com.fenixcore.optibienestar360.modules.corporate.service;

import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import org.springframework.stereotype.Component;

/**
 * Decides who a payment is billed to when its member belongs to a corporate
 * contract (V38). Kept as a standalone collaborator (rather than inlined in
 * {@code PaymentsService}) so the billing rule is unit-testable in isolation
 * and the payment service stays agnostic of the corporate module's internals.
 *
 * <p>The rule, applied to a freshly-built payment before persistence:</p>
 * <ul>
 *   <li>Member has no contract (ordinary Individual / Familiar affiliate) →
 *       no-op.</li>
 *   <li>Contract is {@link PayerMode#INSTITUTION_BULK} → the payment is billed
 *       to the contract: {@code corporate_contract_id} is stamped and, when no
 *       explicit payer was supplied, the payer defaults to the contract's
 *       contact user.</li>
 *   <li>Contract is {@link PayerMode#INDIVIDUAL_PAYER} → the member pays like
 *       any affiliate; the payment is left untouched (no contract link).</li>
 * </ul>
 *
 * <p>Reads only off the already-loaded entity graph — no repository lookups —
 * so it adds no queries beyond the LAZY associations the caller's transaction
 * traverses.</p>
 */
@Component
public class CorporateBillingResolver {

    /** Applies corporate billing in place; safe to call for every payment. */
    public void applyBilling(Payment payment) {
        CorporateContract contract = contractOf(payment);
        if (contract == null) {
            return;   // not a corporate member — nothing to bill to a contract
        }

        if (contract.getPayerMode() == PayerMode.INSTITUTION_BULK) {
            payment.setCorporateContract(contract);
            if (payment.getPayerUser() == null && contract.getContactUser() != null) {
                payment.setPayerUser(contract.getContactUser());
            }
        }
        // INDIVIDUAL_PAYER → member pays as any affiliate; leave the payment as-is.
    }

    private static CorporateContract contractOf(Payment payment) {
        Membership membership = payment.getMembership();
        if (membership == null) return null;
        Member member = membership.getMember();
        return member != null ? member.getCorporateContract() : null;
    }
}
