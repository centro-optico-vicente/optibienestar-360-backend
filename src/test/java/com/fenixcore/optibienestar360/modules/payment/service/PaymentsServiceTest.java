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
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentRejectRequest;
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
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionService;
import com.fenixcore.optibienestar360.modules.promoter.service.DiscountAuthority;
import com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverrideService;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterResolver;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
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
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.fenixcore.optibienestar360.modules.promotion.service.PromotionPricing promotionPricing;
    @Mock private PersonCreditService personCreditService;
    @Mock private com.fenixcore.optibienestar360.modules.bank.service.CompanyBankAccountService companyBankAccountService;
    @Mock private com.fenixcore.optibienestar360.modules.member.service.MemberPromoterService memberPromoterService;

    private PaymentsService sut() {
        return new PaymentsService(paymentRepository, paymentCategoryRepository, paymentMethodRepository,
                promoterRepository, personRepository, bankRepository, membershipRepository, memberRepository,
                userRepository, currencyRepository, currencyConversionService, mapper, defaultSortResolver,
                storageProvider, emailService, messageSource, validatorCacheService, commissionService,
                hierarchyOverrideService, corporateBillingResolver, presignedUrlPolicy, fileValidationService,
                membershipChargeService, notificationChannelResolver, eventPublisher, promotionPricing,
                personCreditService, companyBankAccountService, memberPromoterService);
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
    void applyDiscount_rejects_whenPromoterExceedsDiscountAuthority() {
        Payment payment = pending(new BigDecimal("200.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(promoterRepository.findActiveByUserUuid(ACTOR)).thenReturn(Optional.of(promoterWithCaps("10.00", null)));

        assertThatThrownBy(() -> sut().applyDiscount(payment.getUuid(),
                new PaymentDiscountRequest(new BigDecimal("50.00"), "Ajuste"), ACTOR))   // 25% > 10%
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.discount.exceeds_authority");
    }

    @Test
    void applyDiscount_allows_promoterWithinDiscountAuthority() {
        Payment payment = pending(new BigDecimal("200.00"));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(promoterRepository.findActiveByUserUuid(ACTOR)).thenReturn(Optional.of(promoterWithCaps("30.00", "25.00")));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));

        sut().applyDiscount(payment.getUuid(), new PaymentDiscountRequest(new BigDecimal("50.00"), "Ajuste"), ACTOR);

        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(payment.netAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void discountAuthority_isMostRestrictiveOfRankAndType_zeroWhenNone_uncappedForStaff() {
        assertThat(DiscountAuthority.maxDiscountPct(promoterWithCaps("30.00", "10.00"))).isEqualByComparingTo("10.00");
        assertThat(DiscountAuthority.maxDiscountPct(promoterWithCaps(null, "15.00"))).isEqualByComparingTo("15.00");
        assertThat(DiscountAuthority.maxDiscountPct(promoterWithCaps(null, null))).isEqualByComparingTo("0");
        when(promoterRepository.findActiveByUserUuid(ACTOR)).thenReturn(Optional.empty());
        assertThat(sut().discountAuthority(ACTOR).maxDiscountPct()).isNull();
    }

    private static Promoter promoterWithCaps(String rankCap, String typeCap) {
        PromoterRank rank = new PromoterRank();
        rank.setMaxDiscountPct(rankCap != null ? new BigDecimal(rankCap) : null);
        PromoterType type = new PromoterType();
        type.setMaxDiscountPct(typeCap != null ? new BigDecimal(typeCap) : null);
        Promoter promoter = new Promoter();
        promoter.setRank(rank);
        promoter.setPromoterType(type);
        return promoter;
    }

    private static com.fenixcore.optibienestar360.modules.payment.dto.DownlinePaymentCreateRequest downlineRequest(UUID memberUuid) {
        return new com.fenixcore.optibienestar360.modules.payment.dto.DownlinePaymentCreateRequest(
                memberUuid, new BigDecimal("10.00"), "USD", UUID.randomUUID(), null, null, null, null, null,
                null, null, null, Instant.now(), null, null, null, null, null);
    }

    @Test
    void lookupMembershipForPayer_returnsAnAbbreviatedHolderName() {
        Person person = new Person();
        person.setFirstName("Maria");
        person.setLastName("Perez");
        Member member = new Member();
        member.setId(9L);
        member.setPerson(person);
        com.fenixcore.optibienestar360.modules.membership.entity.Plan plan =
                new com.fenixcore.optibienestar360.modules.membership.entity.Plan();
        plan.setName("Familiar");
        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        membership.setPlan(plan);
        when(memberRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(9L)).thenReturn(Optional.of(membership));

        var found = sut().lookupMembershipForPayer(" 12345678 ", null);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).holderName()).isEqualTo("Maria P.");
        assertThat(found.get(0).planName()).isEqualTo("Familiar");
    }

    @Test
    void lookupMembershipForPayer_returnsAHolderWithoutAnActiveMembership_soTheirCreditCanBeTargeted() {
        Person person = new Person();
        person.setUuid(UUID.randomUUID());
        person.setFirstName("Luis");
        Member member = new Member();
        member.setId(4L);
        member.setPerson(person);
        when(memberRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(4L)).thenReturn(Optional.empty());

        var found = sut().lookupMembershipForPayer("87654321", null);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).personUuid()).isEqualTo(person.getUuid());
        assertThat(found.get(0).membershipUuid()).isNull();
        assertThat(found.get(0).planName()).isNull();
    }

    @Test
    void lookupMembershipForPayer_findsByContractNumber() {
        Person person = new Person();
        person.setFirstName("Ana");
        person.setLastName("Gomez");
        Member member = new Member();
        member.setPerson(person);
        com.fenixcore.optibienestar360.modules.membership.entity.Plan plan =
                new com.fenixcore.optibienestar360.modules.membership.entity.Plan();
        plan.setName("Individual");
        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        membership.setMember(member);
        membership.setPlan(plan);
        when(membershipRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(membership));

        var found = sut().lookupMembershipForPayer(null, " ob-000123 ");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).holderName()).isEqualTo("Ana G.");
        assertThat(found.get(0).membershipUuid()).isEqualTo(membership.getUuid());
    }

    @Test
    void lookupMembershipForPayer_isEmptyForABlankDocument() {
        assertThat(sut().lookupMembershipForPayer("  ", null)).isEmpty();
    }

    @Test
    void registerForDownline_rejectsMemberOfAnotherAdvisor() {
        Promoter me = new Promoter();
        me.setId(1L);
        me.setUuid(UUID.randomUUID());
        Promoter other = new Promoter();
        other.setId(2L);
        Member member = new Member();
        member.setUuid(UUID.randomUUID());
        member.setPromoter(other);
        when(promoterRepository.findActiveByUserUuid(ACTOR)).thenReturn(Optional.of(me));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> sut().registerForDownline(ACTOR, downlineRequest(member.getUuid()), null))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("promoter.member.not_in_portfolio");
        org.mockito.Mockito.verifyNoInteractions(memberPromoterService);
    }

    @Test
    void registerForDownline_takesAnUnassignedMemberIntoThePortfolio() {
        Promoter me = new Promoter();
        me.setId(1L);
        me.setUuid(UUID.randomUUID());
        Promoter institucion = new Promoter();
        institucion.setId(2L);
        institucion.setSystem(true);
        Member member = new Member();
        member.setId(9L);
        member.setUuid(UUID.randomUUID());
        member.setPromoter(institucion);
        when(promoterRepository.findActiveByUserUuid(ACTOR)).thenReturn(Optional.of(me));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        // Stops right after the assignment: the currency lookup is the first thing registerInternal does.
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(9L)).thenReturn(Optional.empty());
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().registerForDownline(ACTOR, downlineRequest(member.getUuid()), null))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("currency.not_found");
        verify(memberPromoterService).assign(member.getUuid(), me.getUuid(), "Assigned on first collection", ACTOR);
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
    void approve_confirmsMember_beforeAttributingCommission() {
        // The commission tier count only counts confirmed enrollments, and this very payment is
        // what confirms the member: confirmation must already be stamped when the engine runs.
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
        var confirmedWhenAttributing = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.mockito.Mockito.doAnswer(invocation -> {
            confirmedWhenAttributing.set(member.getConfirmedAt() != null);
            return Optional.empty();
        }).when(commissionService).calculateAndPersistFor(payment);

        sut().approve(payment.getUuid(), new PaymentApproveRequest(null), ACTOR);

        verify(commissionService).calculateAndPersistFor(payment);
        assertThat(confirmedWhenAttributing).isTrue();
    }

    @Test
    void reject_doesNotConfirmMember_norAttributeCommission() {
        Member member = new Member();
        member.setId(9L);
        member.setUuid(UUID.randomUUID());
        Membership membership = new Membership();
        membership.setMember(member);
        Payment payment = pending(new BigDecimal("5.00"));
        payment.setMembership(membership);
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));

        sut().reject(payment.getUuid(), new PaymentRejectRequest("Comprobante ilegible"), ACTOR);

        assertThat(member.getConfirmedAt()).isNull();
        verify(commissionService, org.mockito.Mockito.never()).calculateAndPersistFor(any());
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

    // ─── Promoter snapshot at registration (ADR 0017) ───────────────────────

    @Test
    void register_snapshotsInstitucion_whenMemberHasNoPromoter() {
        Membership membership = membershipWithMember();
        stubRegisterCollaboratorsFull(membership);
        Promoter institucion = new Promoter();
        institucion.setReferralCode(PromoterResolver.SYSTEM_ADVISOR_CODE);
        institucion.setSystem(true);
        institucion.setActive(true);
        when(promoterRepository.findByReferralCode(PromoterResolver.SYSTEM_ADVISOR_CODE)).thenReturn(Optional.of(institucion));

        sut().register(createRequestWithLines(membership.getUuid(), new BigDecimal("10.00"), List.of(line("10.00"))), null);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPromoter()).isSameAs(institucion);
    }

    @Test
    void register_keepsInactivePromoter_asTheSnapshot() {
        Membership membership = membershipWithMember();
        Promoter inactive = new Promoter();
        inactive.setReferralCode("SALIENTE");
        inactive.setActive(false);
        membership.getMember().setPromoter(inactive);
        stubRegisterCollaboratorsFull(membership);

        sut().register(createRequestWithLines(membership.getUuid(), new BigDecimal("10.00"), List.of(line("10.00"))), null);

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPromoter()).isSameAs(inactive);
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
                null, null, null, null, null, null, null, null);
        PaymentLineRequest reviewedLine = new PaymentLineRequest(reviewedUuid, null, new BigDecimal("5.00"), null,
                null, null, null, null, null, null, null, null);
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
        PaymentCategory category = new PaymentCategory();
        when(paymentCategoryRepository.findByCode(any())).thenReturn(Optional.of(category));
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
                null, null, null, null, null, null, null, null);
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
        u.setUuid(ACTOR);
        return u;
    }
}
