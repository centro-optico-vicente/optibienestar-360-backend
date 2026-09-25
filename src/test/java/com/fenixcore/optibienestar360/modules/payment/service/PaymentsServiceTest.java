package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.FileValidationService;
import com.fenixcore.optibienestar360.common.storage.PresignedUrlPolicy;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.corporate.service.CorporateBillingResolver;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentApproveRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDiscountRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentLineRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentLinesUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCategory;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.mapper.PaymentMapper;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCategoryRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    @Mock private PromoterRepository promoterRepository;
    @Mock private PersonRepository personRepository;
    @Mock private BankRepository bankRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MemberRepository memberRepository;
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
    @Mock private MembershipChargeService membershipChargeService;
    @Mock private NotificationChannelResolver notificationChannelResolver;

    private PaymentsService sut() {
        return new PaymentsService(paymentRepository, paymentCategoryRepository, paymentMethodRepository,
                promoterRepository, personRepository, bankRepository, membershipRepository, memberRepository,
                userRepository, currencyRepository, currencyConversionService, mapper, defaultSortResolver,
                storageProvider, emailService, messageSource, validatorCacheService, commissionService,
                hierarchyOverrideService, corporateBillingResolver, presignedUrlPolicy, fileValidationService,
                membershipChargeService, notificationChannelResolver);
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

    @Test
    void approve_appliesMembershipCharges_forCollectionPayment() {
        Member member = new Member();
        member.setId(9L);
        member.setUuid(UUID.randomUUID());
        Membership membership = new Membership();
        membership.setMember(member);
        Payment payment = pending(new BigDecimal("5.00"));
        payment.setMembership(membership);
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));

        sut().approve(payment.getUuid(), new PaymentApproveRequest(null), ACTOR);

        verify(membershipChargeService).applyPayment(payment);
    }

    // ─── register(): coverageThroughPeriod (V154 advance) ──────────────────

    @Test
    void register_persistsCoverageThroughPeriod_whenSet() {
        Membership membership = membershipWithMember();
        UUID membershipUuid = membership.getUuid();
        stubRegisterCollaboratorsFull(membership);

        PaymentCreateRequest request = createRequest(membershipUuid, false,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 10, 3));

        sut().register(request, null);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAppliedPeriod()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(captor.getValue().getCoverageThroughPeriod()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void register_leavesCoverageThroughPeriodNull_whenOmitted() {
        Membership membership = membershipWithMember();
        UUID membershipUuid = membership.getUuid();
        stubRegisterCollaboratorsFull(membership);

        PaymentCreateRequest request = createRequest(membershipUuid, false,
                LocalDate.of(2026, 8, 15), null);

        sut().register(request, null);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getCoverageThroughPeriod()).isNull();
    }

    @Test
    void register_rejects_whenCoverageThroughPeriodBeforeAppliedPeriod() {
        Membership membership = membershipWithMember();
        UUID membershipUuid = membership.getUuid();
        stubRegisterCollaboratorsMinimal(membership);

        PaymentCreateRequest request = createRequest(membershipUuid, false,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 7, 1));

        assertThatThrownBy(() -> sut().register(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.coverage_through.before_applied_period");
    }

    @Test
    void register_rejects_whenCoverageThroughPeriodSetOnInscription() {
        Membership membership = membershipWithMember();
        UUID membershipUuid = membership.getUuid();
        stubRegisterCollaboratorsMinimal(membership);

        PaymentCreateRequest request = createRequest(membershipUuid, true,
                null, LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> sut().register(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.coverage_through.inscription_forbidden");
    }

    // ─── Multi-line create (V117 lines feature) ─────────────────────────────

    @Test
    void register_multiLine_sumEqualsDeclaredAmount_ok() {
        Membership membership = membershipWithMember();
        stubRegisterCollaboratorsFull(membership);

        PaymentCreateRequest request = createRequestWithLines(membership.getUuid(), new BigDecimal("100.00"),
                List.of(line("60.00"), line("40.00")));

        sut().register(request, null);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().getLines()).hasSize(2);
    }

    @Test
    void register_multiLine_sumLessThanDeclaredAmount_ok() {
        Membership membership = membershipWithMember();
        stubRegisterCollaboratorsFull(membership);

        PaymentCreateRequest request = createRequestWithLines(membership.getUuid(), new BigDecimal("100.00"),
                List.of(line("30.00"), line("40.00")));

        sut().register(request, null);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void register_multiLine_sumExceedsDeclaredAmount_rejected() {
        Membership membership = membershipWithMember();
        stubRegisterCollaboratorsMinimal(membership);

        PaymentCreateRequest request = createRequestWithLines(membership.getUuid(), new BigDecimal("50.00"),
                List.of(line("30.00"), line("40.00")));

        assertThatThrownBy(() -> sut().register(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.lines.sum_exceeds_amount");
    }

    @Test
    void register_multiLine_autoSums_whenNoDeclaredAmount() {
        Membership membership = membershipWithMember();
        stubRegisterCollaboratorsFull(membership);

        PaymentCreateRequest request = createRequestWithLines(membership.getUuid(), null,
                List.of(line("30.00"), line("45.50")));

        sut().register(request, null);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("75.50");
    }

    // ─── Auto-approval (V158): requiresApproval=false payment methods ───────

    @Test
    void register_autoApproves_whenSingleLineMethodDoesNotRequireApproval() {
        Membership membership = membershipWithMember();
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        Currency currency = new Currency();
        currency.setCode("USD");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(currency));
        PaymentMethod exemptMethod = new PaymentMethod();
        exemptMethod.setCode("CASH");
        exemptMethod.setRequiresApproval(false);
        when(paymentMethodRepository.findByUuid(any())).thenReturn(Optional.of(exemptMethod));
        PaymentCategory category = new PaymentCategory();
        when(paymentCategoryRepository.findByCode(any())).thenReturn(Optional.of(category));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));

        PaymentCreateRequest request = createRequestWithLines(membership.getUuid(), new BigDecimal("10.00"),
                List.of(line("10.00")));

        sut().register(request, null, false, ACTOR);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.APPROVED.name());
        assertThat(saved.getReviewedBy()).isNotNull();
        assertThat(saved.getReviewedBy().getUuid()).isEqualTo(ACTOR);
        assertThat(saved.getLines()).allMatch(l -> PaymentStatus.APPROVED.name().equals(l.getStatus()));
        verify(commissionService).calculateAndPersistFor(saved);
        verify(membershipChargeService).applyPayment(saved);
        verify(validatorCacheService).evictForMembership(membership);
    }

    @Test
    void register_legacySingleFlatLine_autoApproves_whenMethodDoesNotRequireApproval() {
        Membership membership = membershipWithMember();
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        Currency currency = new Currency();
        currency.setCode("USD");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(currency));
        PaymentMethod exemptMethod = new PaymentMethod();
        exemptMethod.setCode("CASH");
        exemptMethod.setRequiresApproval(false);
        when(paymentMethodRepository.findByUuid(any())).thenReturn(Optional.of(exemptMethod));
        PaymentCategory category = new PaymentCategory();
        when(paymentCategoryRepository.findByCode(any())).thenReturn(Optional.of(category));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));

        // No `lines` on the request — the legacy flat method/amount fields build the
        // single line internally (still supported post-PR#290, see registerInternal).
        PaymentCreateRequest request = createRequest(membership.getUuid(), false,
                LocalDate.of(2026, 8, 15), null);

        sut().register(request, null, false, ACTOR);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.APPROVED.name());
    }

    @Test
    void register_staysPending_whenLinesUseMixedApprovalMethods() {
        Membership membership = membershipWithMember();
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        Currency currency = new Currency();
        currency.setCode("USD");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(currency));
        PaymentMethod exemptMethod = new PaymentMethod();
        exemptMethod.setCode("CASH");
        exemptMethod.setRequiresApproval(false);
        PaymentMethod reviewedMethod = new PaymentMethod();
        reviewedMethod.setCode("BANK_TRANSFER");
        reviewedMethod.setRequiresApproval(true);
        UUID exemptUuid = UUID.randomUUID();
        UUID reviewedUuid = UUID.randomUUID();
        when(paymentMethodRepository.findByUuid(exemptUuid)).thenReturn(Optional.of(exemptMethod));
        when(paymentMethodRepository.findByUuid(reviewedUuid)).thenReturn(Optional.of(reviewedMethod));
        PaymentCategory category = new PaymentCategory();
        when(paymentCategoryRepository.findByCode(any())).thenReturn(Optional.of(category));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentLineRequest exemptLine = new PaymentLineRequest(exemptUuid, null, new BigDecimal("5.00"), null,
                null, null, null, null, null, null, null);
        PaymentLineRequest reviewedLine = new PaymentLineRequest(reviewedUuid, null, new BigDecimal("5.00"), null,
                null, null, null, null, null, null, null);
        PaymentCreateRequest request = createRequestWithLines(membership.getUuid(), new BigDecimal("10.00"),
                List.of(exemptLine, reviewedLine));

        sut().register(request, null, false, ACTOR);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING.name());
    }

    @Test
    void register_staysDraft_evenWhenEveryLineMethodIsExempt() {
        Membership membership = membershipWithMember();
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        Currency currency = new Currency();
        currency.setCode("USD");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(currency));
        PaymentMethod exemptMethod = new PaymentMethod();
        exemptMethod.setCode("CASH");
        exemptMethod.setRequiresApproval(false);
        when(paymentMethodRepository.findByUuid(any())).thenReturn(Optional.of(exemptMethod));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentCreateRequest request = createRequestWithLines(membership.getUuid(), new BigDecimal("10.00"),
                List.of(line("10.00")));

        sut().register(request, null, true, ACTOR);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.DRAFT.name());
        verify(commissionService, org.mockito.Mockito.never()).calculateAndPersistFor(any());
    }

    // ─── Lines lifecycle: updateLines / submit / reactivateToDraft (V117) ───

    @Test
    void updateLines_succeeds_onDraft() {
        Payment payment = draft(new BigDecimal("10.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        PaymentMethod method = new PaymentMethod();
        method.setCode("CASH");
        when(paymentMethodRepository.findByUuid(any())).thenReturn(Optional.of(method));

        PaymentLinesUpdateRequest request = new PaymentLinesUpdateRequest(
                List.of(line("15.00"), line("25.00")), new BigDecimal("40.00"));

        sut().updateLines(payment.getUuid(), request, ACTOR);

        assertThat(payment.getLines()).hasSize(2);
        assertThat(payment.getAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void updateLines_rejected_whenPending() {
        Payment payment = pending(new BigDecimal("10.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        PaymentLinesUpdateRequest request = new PaymentLinesUpdateRequest(List.of(line("10.00")), null);

        assertThatThrownBy(() -> sut().updateLines(payment.getUuid(), request, ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.lines.not_draft");
    }

    @Test
    void updateLines_rejected_whenApproved() {
        Payment payment = draft(new BigDecimal("10.00"));
        payment.setStatus(PaymentStatus.APPROVED.name());
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        PaymentLinesUpdateRequest request = new PaymentLinesUpdateRequest(List.of(line("10.00")), null);

        assertThatThrownBy(() -> sut().updateLines(payment.getUuid(), request, ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.lines.not_draft");
    }

    @Test
    void updateLines_rejected_whenRejected() {
        Payment payment = draft(new BigDecimal("10.00"));
        payment.setStatus(PaymentStatus.REJECTED.name());
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        PaymentLinesUpdateRequest request = new PaymentLinesUpdateRequest(List.of(line("10.00")), null);

        assertThatThrownBy(() -> sut().updateLines(payment.getUuid(), request, ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.lines.not_draft");
    }

    @Test
    void submit_movesDraftToPending() {
        Payment payment = draft(new BigDecimal("10.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        sut().submit(payment.getUuid(), ACTOR);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING.name());
    }

    @Test
    void submit_rejected_whenNotDraft() {
        Payment payment = pending(new BigDecimal("10.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> sut().submit(payment.getUuid(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.lines.not_draft");
    }

    @Test
    void reactivateToDraft_movesPendingToDraft() {
        Payment payment = pending(new BigDecimal("10.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        sut().reactivateToDraft(payment.getUuid(), ACTOR);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DRAFT.name());
    }

    @Test
    void reactivateToDraft_rejected_whenAlreadyDraft() {
        Payment payment = draft(new BigDecimal("10.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> sut().reactivateToDraft(payment.getUuid(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.reactivate.already_draft");
    }

    @Test
    void reactivateToDraft_rejected_whenApproved() {
        Payment payment = draft(new BigDecimal("10.00"));
        payment.setStatus(PaymentStatus.APPROVED.name());
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> sut().reactivateToDraft(payment.getUuid(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.reactivate.terminal");
    }

    @Test
    void reactivateToDraft_rejected_whenRejected() {
        Payment payment = draft(new BigDecimal("10.00"));
        payment.setStatus(PaymentStatus.REJECTED.name());
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> sut().reactivateToDraft(payment.getUuid(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.reactivate.terminal");
    }

    private static PaymentLineRequest line(String amount) {
        return new PaymentLineRequest(UUID.randomUUID(), null, new BigDecimal(amount), null,
                null, null, null, null, null, null, null);
    }

    private static PaymentCreateRequest createRequestWithLines(UUID membershipUuid, BigDecimal amount,
                                                                List<PaymentLineRequest> lines) {
        return new PaymentCreateRequest(membershipUuid, amount, "USD",
                UUID.randomUUID(), null, null, null, null, null, null, null, null,
                Instant.parse("2026-08-20T00:00:00Z"), false, null, null,
                null, null, lines);
    }

    private static Payment draft(BigDecimal amount) {
        Payment p = new Payment();
        p.setUuid(UUID.randomUUID());
        p.setAmount(amount);
        p.setStatus(PaymentStatus.DRAFT.name());
        return p;
    }

    private Membership membershipWithMember() {
        Member member = new Member();
        member.setId(9L);
        member.setUuid(UUID.randomUUID());
        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        membership.setMember(member);
        return membership;
    }

    /** Stubs collaborators consulted before the {@code inscription}/{@code coverageThroughPeriod} validation runs. */
    private void stubRegisterCollaboratorsMinimal(Membership membership) {
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        Currency currency = new Currency();
        currency.setCode("USD");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(currency));
        PaymentMethod method = new PaymentMethod();
        method.setCode("CASH");
        when(paymentMethodRepository.findByUuid(any())).thenReturn(Optional.of(method));
    }

    /** Full happy-path stubbing — adds the collaborators reached only after validation passes. */
    private void stubRegisterCollaboratorsFull(Membership membership) {
        stubRegisterCollaboratorsMinimal(membership);
        PaymentCategory category = new PaymentCategory();
        when(paymentCategoryRepository.findByCode(any())).thenReturn(Optional.of(category));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PaymentCreateRequest createRequest(UUID membershipUuid, boolean inscription,
                                                       LocalDate appliedPeriod, LocalDate coverageThroughPeriod) {
        return new PaymentCreateRequest(membershipUuid, new BigDecimal("10.00"), "USD",
                UUID.randomUUID(), null, null, null, null, null, null, null, null,
                Instant.parse("2026-08-20T00:00:00Z"), inscription, appliedPeriod, coverageThroughPeriod,
                null, null, null);
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
