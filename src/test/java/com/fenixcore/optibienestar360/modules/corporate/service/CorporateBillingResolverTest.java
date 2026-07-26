package com.fenixcore.optibienestar360.modules.corporate.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorporateBillingResolver} — the V38 billing rule that
 * decides whether a payment is billed to a corporate contract. Pure logic over
 * the entity graph; no mocks needed.
 */
class CorporateBillingResolverTest {

    private final CorporateBillingResolver resolver = new CorporateBillingResolver();

    @Test
    void institutionBulk_noExplicitPayer_billsContractAndDefaultsPayerToContactUser() {
        User contact = user();
        Payment payment = paymentFor(contract(PayerMode.INSTITUTION_BULK, contact));

        resolver.applyBilling(payment);

        assertThat(payment.getCorporateContract()).isNotNull();
        assertThat(payment.getPayerUser()).isSameAs(contact);
    }

    @Test
    void institutionBulk_keepsExplicitPayer() {
        User explicit = user();
        Payment payment = paymentFor(contract(PayerMode.INSTITUTION_BULK, user()));
        payment.setPayerUser(explicit);

        resolver.applyBilling(payment);

        assertThat(payment.getCorporateContract()).isNotNull();
        assertThat(payment.getPayerUser()).isSameAs(explicit);   // not overwritten by the contact user
    }

    @Test
    void institutionBulk_noContactUser_leavesPayerNull() {
        Payment payment = paymentFor(contract(PayerMode.INSTITUTION_BULK, null));

        resolver.applyBilling(payment);

        assertThat(payment.getCorporateContract()).isNotNull();
        assertThat(payment.getPayerUser()).isNull();
    }

    @Test
    void individualPayer_isNoOp() {
        Payment payment = paymentFor(contract(PayerMode.INDIVIDUAL_PAYER, user()));

        resolver.applyBilling(payment);

        assertThat(payment.getCorporateContract()).isNull();   // member pays like any affiliate
        assertThat(payment.getPayerUser()).isNull();
    }

    @Test
    void memberWithoutContract_isNoOp() {
        Member member = new Member();
        Membership membership = new Membership();
        membership.setMember(member);
        Payment payment = new Payment();
        payment.setMembership(membership);

        resolver.applyBilling(payment);

        assertThat(payment.getCorporateContract()).isNull();
    }

    @Test
    void nullMembership_isNoOp() {
        Payment payment = new Payment();

        resolver.applyBilling(payment);   // must not throw

        assertThat(payment.getCorporateContract()).isNull();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CorporateContract contract(PayerMode mode, User contact) {
        CorporateContract c = new CorporateContract();
        c.setUuid(UUID.randomUUID());
        c.setPayerMode(mode);
        c.setContactUser(contact);
        return c;
    }

    private Payment paymentFor(CorporateContract contract) {
        Member member = new Member();
        member.setCorporateContract(contract);
        Membership membership = new Membership();
        membership.setMember(member);
        Payment payment = new Payment();
        payment.setMembership(membership);
        return payment;
    }

    private User user() {
        User u = new User();
        u.setUuid(UUID.randomUUID());
        return u;
    }
}
