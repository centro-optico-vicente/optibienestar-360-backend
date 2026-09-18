package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.FileValidationService;
import com.fenixcore.optibienestar360.common.storage.PresignedUrlPolicy;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.corporate.service.CorporateBillingResolver;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentApproveRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDiscountRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optibienestar360.modules.payment.mapper.PaymentMapper;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCategoryRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionService;
import com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverrideService;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the one-off payment discount (V41, permission
 * {@code ALLOWS_DISCOUNT}): only PENDING payments are eligible, the amount
 * cannot exceed the payment total, and the discount is captured on the row.
 */
@ExtendWith(MockitoExtension.class)
class PaymentsServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentCategoryRepository paymentCategoryRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService currencyConversionService;
    @Mock private PaymentMapper mapper;
    @Mock private ObjectProvider<StorageService> storageProvider;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;
    @Mock private ValidatorCacheService validatorCacheService;
    @Mock private CommissionService commissionService;
    @Mock private HierarchyOverrideService hierarchyOverrideService;
    @Mock private CorporateBillingResolver corporateBillingResolver;
    @Mock private PresignedUrlPolicy presignedUrlPolicy;
    @Mock private FileValidationService fileValidationService;
    @Mock private DefaultSortResolver defaultSortResolver;

    private PaymentsService sut() {
        return new PaymentsService(paymentRepository, paymentCategoryRepository, paymentMethodRepository,
                membershipRepository, userRepository, currencyRepository,
                currencyConversionService, mapper, defaultSortResolver, storageProvider, emailService, messageSource,
                validatorCacheService, commissionService, hierarchyOverrideService, corporateBillingResolver,
                presignedUrlPolicy, fileValidationService);
    }

    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void applyDiscount_setsFields_onPendingPayment() {
        Payment payment = pending(new BigDecimal("20.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));
        // mapper.toDto returns null by default — the test asserts on the entity, not the DTO.

        sut().applyDiscount(payment.getUuid(), new PaymentDiscountRequest(new BigDecimal("5.00"), "Ajuste"), ACTOR);

        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(payment.getDiscountReason()).isEqualTo("Ajuste");
        assertThat(payment.getDiscountedBy()).isNotNull();
        assertThat(payment.getDiscountedAt()).isNotNull();
    }

    @Test
    void applyDiscount_rejects_whenNotPending() {
        Payment payment = pending(new BigDecimal("20.00"));
        payment.setStatus(PaymentStatus.APPROVED.name());
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> sut().applyDiscount(payment.getUuid(),
                new PaymentDiscountRequest(new BigDecimal("5.00"), "Ajuste"), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.discount.not_pending");
    }

    @Test
    void applyDiscount_rejects_whenExceedsAmount() {
        Payment payment = pending(new BigDecimal("20.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> sut().applyDiscount(payment.getUuid(),
                new PaymentDiscountRequest(new BigDecimal("25.00"), "Ajuste"), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.discount.exceeds_amount");
    }

    @Test
    void approve_confirmsMember_onFirstApprovedPayment() {
        Member member = new Member();
        member.setId(9L);
        member.setUuid(UUID.randomUUID());
        Membership membership = new Membership();
        membership.setMember(member);
        Payment payment = pending(new BigDecimal("5.00"));
        payment.setMembership(membership);
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));
        when(paymentRepository.countApprovedByMemberId(9L)).thenReturn(1L);

        sut().approve(payment.getUuid(), new PaymentApproveRequest(null), ACTOR);

        assertThat(member.getConfirmedAt()).isNotNull();
    }

    @Test
    void approve_doesNotReconfirm_whenAlreadyConfirmed() {
        Member member = new Member();
        member.setId(9L);
        member.setUuid(UUID.randomUUID());
        var alreadyConfirmedAt = java.time.Instant.parse("2026-01-01T00:00:00Z");
        member.setConfirmedAt(alreadyConfirmedAt);
        Membership membership = new Membership();
        membership.setMember(member);
        Payment payment = pending(new BigDecimal("5.00"));
        payment.setMembership(membership);
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));

        sut().approve(payment.getUuid(), new PaymentApproveRequest(null), ACTOR);

        assertThat(member.getConfirmedAt()).isEqualTo(alreadyConfirmedAt);
    }

    @Test
    void approve_doesNotConfirmMember_whenNotFirstApprovedPayment() {
        Member member = new Member();
        member.setId(9L);
        member.setUuid(UUID.randomUUID());
        Membership membership = new Membership();
        membership.setMember(member);
        Payment payment = pending(new BigDecimal("5.00"));
        payment.setMembership(membership);
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));
        when(paymentRepository.countApprovedByMemberId(9L)).thenReturn(2L);

        sut().approve(payment.getUuid(), new PaymentApproveRequest(null), ACTOR);

        assertThat(member.getConfirmedAt()).isNull();
    }

    private static Payment pending(BigDecimal amount) {
        Payment p = new Payment();
        p.setUuid(UUID.randomUUID());
        p.setAmount(amount);
        p.setStatus(PaymentStatus.PENDING.name());
        return p;
    }

    private static User user() {
        User u = new User();
        u.setId(1L);
        u.setUuid(UUID.randomUUID());
        return u;
    }
}
