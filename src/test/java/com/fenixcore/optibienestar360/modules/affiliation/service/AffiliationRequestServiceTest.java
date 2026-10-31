package com.fenixcore.optibienestar360.modules.affiliation.service;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.FileValidationService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.PresignedUrlPolicy;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationPaymentRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestUpdateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AssistedAffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog;
import com.fenixcore.optibienestar360.modules.affiliation.event.AffiliationRequestEvent;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestLogRepository;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipsService;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentsService;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.MemberContractService;
import com.fenixcore.optibienestar360.modules.terms.service.MemberTermsApplicant;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AffiliationRequestService}: the public submit (evidence, duplicates
 * against members/users and against PENDING requests), the reviewer's edit
 * log, approval (acceptance recorded only when the contract text is
 * unchanged) and rejection (reason required).
 */
@ExtendWith(MockitoExtension.class)
class AffiliationRequestServiceTest {

    private static final String CONTRACT = "Contrato de María Pérez, V-12345678";

    @Mock private AffiliationRequestRepository repository;
    @Mock private AffiliationRequestLogRepository logRepository;
    @Mock private PersonRepository personRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private PlanRepository planRepository;
    @Mock private UserService userService;
    @Mock private MembersService membersService;
    @Mock private MembershipsService membershipsService;
    @Mock private TermsVersionService termsVersionService;
    @Mock private TermsAcceptanceService termsAcceptanceService;
    @Mock private MemberContractService memberContractService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DefaultSortResolver defaultSortResolver;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuthService authService;
    @Mock private PaymentsService paymentsService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private FileValidationService fileValidationService;
    @Mock private ObjectProvider<StorageService> storageProvider;
    @Mock private PresignedUrlPolicy presignedUrlPolicy;

    @InjectMocks
    private AffiliationRequestService service;

    private Plan plan;
    private TermsVersion terms;

    @BeforeEach
    void setUp() {
        plan = new Plan();
        plan.setId(3L);
        plan.setUuid(UUID.randomUUID());
        plan.setName("Plan Individual");
        plan.setType(PlanType.INDIVIDUAL);
        plan.setActive(true);
        plan.setPublished(true);
        plan.setInscriptionFee(new BigDecimal("25.00"));
        Currency usd = new Currency();
        usd.setCode("USD");
        plan.setCurrency(usd);

        terms = new TermsVersion();
        terms.setId(9L);
        terms.setUuid(UUID.randomUUID());
        terms.setTermType(TermType.MEMBER);
        terms.setTitle("Acuerdo de Membresía");
        terms.setContentMarkdown("Contrato de {{member.fullName}}, {{member.document}}");
    }

    private AffiliationRequestCreateRequest createRequest(String referralCode) {
        return new AffiliationRequestCreateRequest("María", "Pérez", "V", "12345678",
                LocalDate.of(1985, 3, 15), " Maria@Example.com ", "0414-1234567", "s3cret-pass",
                plan.getUuid(), referralCode, terms.getUuid(), null);
    }

    private AffiliationRequestCreateRequest createRequestWithPayment(AffiliationPaymentRequest payment) {
        return new AffiliationRequestCreateRequest("María", "Pérez", "V", "12345678",
                LocalDate.of(1985, 3, 15), " Maria@Example.com ", "0414-1234567", "s3cret-pass",
                plan.getUuid(), null, terms.getUuid(), payment);
    }

    private static AffiliationPaymentRequest paymentRequest(UUID methodUuid, String reference) {
        return new AffiliationPaymentRequest(methodUuid, null, "V12345678", null, null, null,
                "0414-1234567", null, reference, Instant.parse("2026-10-19T10:00:00Z"));
    }

    private AffiliationRequest pending() {
        AffiliationRequest r = new AffiliationRequest();
        r.setId(11L);
        r.setUuid(UUID.randomUUID());
        r.setFirstName("María");
        r.setLastName("Pérez");
        r.setDocumentType("V");
        r.setDocumentNumber("12345678");
        r.setBirthDate(LocalDate.of(1985, 3, 15));
        r.setEmail("maria@example.com");
        r.setLocale("es");
        r.setPlan(plan);
        r.setPasswordHash("$2a$10$hash");
        r.setTermsVersion(terms);
        r.setTermsRenderedContent(CONTRACT);
        r.setTermsContentSha256(MemberContractService.sha256(CONTRACT));
        r.setTermsAcceptedAt(Instant.parse("2026-10-20T14:00:00Z"));
        r.setIpAddress("200.1.2.3");
        r.setUserAgent("Mozilla/5.0");
        r.setStatus(AffiliationRequest.Status.PENDING.name());
        return r;
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Test
    void create_storesTheHashAndTheContractEvidence_andAnnouncesTheRequest() {
        Promoter advisor = new Promoter();
        advisor.setDisplayName("Ana Ventas");
        when(promoterRepository.findByReferralCode("ANAV42")).thenReturn(Optional.of(advisor));
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(termsVersionService.requireCurrentPublic(TermType.MEMBER, terms.getUuid())).thenReturn(terms);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("$2a$10$hash");
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        AffiliationRequestCreatedDto created = service.create(createRequest(" anav42 "),
                new ClientRequestMetadata("200.1.2.3", "Mozilla/5.0"));

        ArgumentCaptor<AffiliationRequest> saved = ArgumentCaptor.forClass(AffiliationRequest.class);
        verify(repository).save(saved.capture());
        AffiliationRequest r = saved.getValue();
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getEmail()).isEqualTo("maria@example.com");
        assertThat(r.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(r.getReferralCode()).isEqualTo("ANAV42");
        assertThat(r.getPromoter()).isSameAs(advisor);
        assertThat(r.getTermsRenderedContent()).isEqualTo(CONTRACT);
        assertThat(r.getTermsContentSha256()).isEqualTo(MemberContractService.sha256(CONTRACT)).hasSize(64);
        assertThat(r.getIpAddress()).isEqualTo("200.1.2.3");
        assertThat(r.getTermsAcceptedAt()).isNotNull();

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.email()).isEqualTo("maria@example.com");
        assertThat(created.advisorName()).isEqualTo("Ana Ventas");
        verify(logRepository).save(any(AffiliationRequestLog.class));
        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AffiliationRequestEvent.Kind.RECEIVED);
        // Nothing is created until a reviewer approves it.
        verify(userService, never()).createUserWithPasswordHash(any(), anyString(), any());
        verify(membersService, never()).create(any());
    }

    @Test
    void create_rejectsAnExistingMemberDocument() {
        when(personRepository.existsByDocumentTypeAndDocumentNumber("V", "12345678")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation.document.exists");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsAnExistingUserEmail() {
        when(userRepository.existsByEmail("maria@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .hasMessage("user.email.exists");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsADocumentWithAnotherPendingRequest() {
        when(repository.existsByDocumentTypeAndDocumentNumberAndStatus("V", "12345678", "PENDING")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation_request.document.pending");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsAnEmailWithAnotherPendingRequest() {
        when(repository.existsByEmailIgnoreCaseAndStatus("maria@example.com", "PENDING")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation_request.email.pending");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsAnUnknownReferralCode() {
        when(promoterRepository.findByReferralCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest("nope"), ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation.referral.not_found");
    }

    // ─── Create with reported inscription payment (V203) ──────────────────

    private void stubStoreUpToThePayment() {
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(termsVersionService.requireCurrentPublic(TermType.MEMBER, terms.getUuid())).thenReturn(terms);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("$2a$10$hash");
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);
    }

    @Test
    void create_withPayment_runsThePaymentRules_missingReferenceIs422() {
        UUID methodUuid = UUID.randomUUID();
        stubStoreUpToThePayment();
        when(paymentsService.resolveMethodSelection(methodUuid, null, null, "0414-1234567", null, null))
                .thenThrow(new IllegalArgumentException("payment.reference.required"));

        assertThatThrownBy(() -> service.create(createRequestWithPayment(paymentRequest(methodUuid, null)),
                ClientRequestMetadata.EMPTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.reference.required");
        verify(repository, never()).save(any());
    }

    @Test
    void create_withPayment_storesThePlanInscriptionFee_andUploadsTheProofUnderTheRequest() throws Exception {
        PaymentMethod method = new PaymentMethod();
        method.setUuid(UUID.randomUUID());
        method.setName("Pago móvil");
        stubStoreUpToThePayment();
        when(paymentsService.resolveMethodSelection(method.getUuid(), null, " REF-123 ", "0414-1234567", null, null))
                .thenReturn(new PaymentsService.MethodSelection(method, null));
        StorageService storage = mock(StorageService.class);
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        MockMultipartFile proof = new MockMultipartFile("support", "comprobante.pdf", "application/pdf",
                new byte[]{1, 2, 3});

        service.create(createRequestWithPayment(paymentRequest(method.getUuid(), " REF-123 ")), proof,
                ClientRequestMetadata.EMPTY);

        ArgumentCaptor<AffiliationRequest> saved = ArgumentCaptor.forClass(AffiliationRequest.class);
        verify(repository).save(saved.capture());
        AffiliationRequest r = saved.getValue();
        assertThat(r.getPaymentMethod()).isSameAs(method);
        assertThat(r.getPaymentAmount()).isEqualByComparingTo("25.00");
        assertThat(r.getPaymentCurrencyCode()).isEqualTo("USD");
        assertThat(r.getPaymentReferenceNumber()).isEqualTo("REF-123");
        assertThat(r.getPaymentDate()).isEqualTo(Instant.parse("2026-10-19T10:00:00Z"));
        assertThat(r.getPaymentSupportFileName()).isEqualTo("comprobante.pdf");
        assertThat(r.getPaymentSupportFileSizeBytes()).isEqualTo(3L);
        assertThat(r.getPaymentSupportFileUrl()).contains("/affiliation_requests/" + r.getUuid() + "/");
        verify(fileValidationService).validate(FileVisibility.CONFIDENTIAL, proof);
        verify(storage).upload(eq(r.getPaymentSupportFileUrl()), any(InputStream.class), eq(3L), eq("application/pdf"));
    }

    @Test
    void create_withADirectReceiptMethod_is422_soAnApplicantCannotSelfApprove() {
        PaymentMethod cash = new PaymentMethod();
        cash.setUuid(UUID.randomUUID());
        cash.setRequiresApproval(false);
        stubStoreUpToThePayment();
        when(paymentsService.resolveMethodSelection(cash.getUuid(), null, "REF-1", "0414-1234567", null, null))
                .thenReturn(new PaymentsService.MethodSelection(cash, null));

        assertThatThrownBy(() -> service.create(createRequestWithPayment(paymentRequest(cash.getUuid(), "REF-1")),
                ClientRequestMetadata.EMPTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.payment.method_not_allowed");
        verify(repository, never()).save(any());
    }

    @Test
    void create_storesTheProofWithTheTypeOfItsExtension_notTheClientsDeclaredOne() throws Exception {
        PaymentMethod method = new PaymentMethod();
        method.setUuid(UUID.randomUUID());
        stubStoreUpToThePayment();
        when(paymentsService.resolveMethodSelection(method.getUuid(), null, "REF-1", "0414-1234567", null, null))
                .thenReturn(new PaymentsService.MethodSelection(method, null));
        StorageService storage = mock(StorageService.class);
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        // A page disguised as an image: served as text/html it would run when staff open it.
        MockMultipartFile proof = new MockMultipartFile("support", "comprobante.png", "text/html",
                "<script>alert(1)</script>".getBytes());

        service.create(createRequestWithPayment(paymentRequest(method.getUuid(), "REF-1")), proof,
                ClientRequestMetadata.EMPTY);

        verify(storage).upload(any(String.class), any(InputStream.class), any(Long.class), eq("image/png"));
    }

    @Test
    void create_withAProofButNoPayment_is422() {
        MockMultipartFile proof = new MockMultipartFile("support", "comprobante.pdf", "application/pdf",
                new byte[]{1});

        assertThatThrownBy(() -> service.create(createRequest(null), proof, ClientRequestMetadata.EMPTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.payment.support_without_payment");
        verify(repository, never()).save(any());
        verify(fileValidationService, never()).validate(any(), any(org.springframework.web.multipart.MultipartFile.class));
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Test
    void update_logsOnlyTheChangedFields() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn("Contrato corregido");

        service.update(r.getUuid(), new AffiliationRequestUpdateRequest("María", "Pérez", "V", "12345679",
                r.getBirthDate(), r.getEmail(), null, plan.getUuid(), "Cédula mal escrita"), reviewer.getUuid());

        assertThat(r.getDocumentNumber()).isEqualTo("12345679");
        ArgumentCaptor<AffiliationRequestLog> entry = ArgumentCaptor.forClass(AffiliationRequestLog.class);
        verify(logRepository).save(entry.capture());
        assertThat(entry.getValue().getAction()).isEqualTo(AffiliationRequestLog.Action.UPDATED);
        assertThat(entry.getValue().getComment()).isEqualTo("Cédula mal escrita");
        assertThat(entry.getValue().getActor()).isSameAs(reviewer);
        assertThat(entry.getValue().getChanges()).containsExactly(
                Map.of("field", "documentNumber", "from", "12345678", "to", "12345679"));
    }

    @Test
    void update_onlyWhilePending() {
        AffiliationRequest r = pending();
        r.setStatus("APPROVED");
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.update(r.getUuid(), new AffiliationRequestUpdateRequest("María", "Pérez",
                "V", "12345678", r.getBirthDate(), r.getEmail(), null, plan.getUuid(), null), UUID.randomUUID()))
                .hasMessage("affiliation_request.update.invalid_state");
    }

    // ─── Approve ────────────────────────────────────────────────────────────

    private User stubApprovalCreation(AffiliationRequest r, User reviewer) {
        return stubApprovalCreation(r, reviewer, "$2a$10$hash");
    }

    private User stubApprovalCreation(AffiliationRequest r, User reviewer, String expectedHash) {
        Role memberRole = new Role();
        memberRole.setUuid(UUID.randomUUID());
        User created = new User();
        created.setUuid(UUID.randomUUID());
        Member member = new Member();
        member.setUuid(UUID.randomUUID());
        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        UserDto userDto = mock(UserDto.class);
        when(userDto.uuid()).thenReturn(created.getUuid());
        MemberDetailDto memberDto = mock(MemberDetailDto.class);
        when(memberDto.uuid()).thenReturn(member.getUuid());
        MembershipDto membershipDto = mock(MembershipDto.class);
        when(membershipDto.uuid()).thenReturn(membership.getUuid());

        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(roleRepository.findByName("AFILIADO")).thenReturn(Optional.of(memberRole));
        when(userService.createUserWithPasswordHash(any(AdminCreateUserRequest.class), eq(expectedHash),
                eq(reviewer.getUuid()))).thenReturn(userDto);
        when(membersService.create(any())).thenReturn(memberDto);
        when(membershipsService.enroll(eq(member.getUuid()), any(), eq(reviewer.getUuid()))).thenReturn(membershipDto);
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        when(userRepository.findByUuid(created.getUuid())).thenReturn(Optional.of(created));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        return created;
    }

    @Test
    void approve_withUnchangedData_recordsTheApplicantsAcceptance() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        User created = stubApprovalCreation(r, reviewer);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        service.approve(r.getUuid(), null, reviewer.getUuid());

        ArgumentCaptor<ClientRequestMetadata> evidence = ArgumentCaptor.forClass(ClientRequestMetadata.class);
        verify(termsAcceptanceService).recordFormAcceptance(eq(created), eq(terms), eq(r.getMembership()),
                evidence.capture(), eq(Instant.parse("2026-10-20T14:00:00Z")), eq(r.getTermsRenderedContent()),
                eq(TermsAcceptance.Channel.SELF_SERVICE));
        verify(authService, never()).issuePasswordSetupLink(any(), any(), any());
        assertThat(evidence.getValue().ipAddress()).isEqualTo("200.1.2.3");
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getUser()).isSameAs(created);
        assertThat(r.getMembership().getStatus()).isEqualTo(Membership.LifecycleStatus.SUSPENDED.name());
        assertThat(r.getReviewedBy()).isSameAs(reviewer);
        assertThat(r.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_afterCorrectingTheDocument_leavesTheContractPendingForFirstLogin() {
        AffiliationRequest r = pending();
        r.setDocumentNumber("12345679");
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        stubApprovalCreation(r, reviewer);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class)))
                .thenReturn("Contrato de María Pérez, V-12345679");

        service.approve(r.getUuid(), "Aprobada con cédula corregida", reviewer.getUuid());

        verify(termsAcceptanceService, never()).recordFormAcceptance(any(), any(), any(), any(), any(), any(), any());
        verify(termsAcceptanceService, never()).recordSelfServiceAcceptance(any(), any(), any(), any(), any(), any());
        verify(termsAcceptanceService, never()).recordSelfServiceAcceptance(any(), any(), any(), any());
        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approve_twice_isRejectedWithoutCreatingAnything() {
        AffiliationRequest r = pending();
        r.setStatus("APPROVED");
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(r.getUuid(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.approve.invalid_state");
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
    }

    @Test
    void approve_whenTheDocumentWasRegisteredMeanwhile_isRejected() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(personRepository.existsByDocumentTypeAndDocumentNumber("V", "12345678")).thenReturn(true);

        assertThatThrownBy(() -> service.approve(r.getUuid(), null, reviewer.getUuid()))
                .hasMessage("affiliation.document.exists");
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
    }

    @Test
    void approve_assistedWithoutPassword_emailsAnActivationLink_andRecordsAnAssistedAcceptance() {
        AffiliationRequest r = pending();
        r.setChannel(AffiliationRequest.Channel.ASSISTED.name());
        r.setPasswordHash(null);
        Promoter advisor = new Promoter();
        advisor.setDisplayName("Ana Ventas");
        advisor.setReferralCode("ANAV42");
        r.setPromoter(advisor);
        r.setReferralCode("ANAV42");
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$random");
        User created = stubApprovalCreation(r, reviewer, "$2a$10$random");
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);
        when(authService.issuePasswordSetupLink(eq(created), eq(Duration.ofHours(72)), any()))
                .thenReturn("https://app.example.test/restablecer-clave?token=abc");

        service.approve(r.getUuid(), null, reviewer.getUuid());

        verify(termsAcceptanceService).recordFormAcceptance(eq(created), eq(terms), eq(r.getMembership()),
                any(ClientRequestMetadata.class), eq(Instant.parse("2026-10-20T14:00:00Z")), eq(CONTRACT),
                eq(TermsAcceptance.Channel.ASSISTED));
        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AffiliationRequestEvent.Kind.APPROVED);
        assertThat(event.getValue().channel()).isEqualTo("ASSISTED");
        assertThat(event.getValue().activationUrl()).isEqualTo("https://app.example.test/restablecer-clave?token=abc");
        // The member is attributed through the advisor's own referral code.
        ArgumentCaptor<com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest> member =
                ArgumentCaptor.forClass(com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest.class);
        verify(membersService).create(member.capture());
        assertThat(member.getValue().referralCode()).isEqualTo("ANAV42");
        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approve_withPassword_hasNoActivationLink() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        stubApprovalCreation(r, reviewer);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        service.approve(r.getUuid(), null, reviewer.getUuid());

        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().activationUrl()).isNull();
        assertThat(event.getValue().channel()).isEqualTo("SELF_SERVICE");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void approve_withAReportedPayment_registersTheInscriptionPayment_andLinksIt() {
        AffiliationRequest r = pending();
        PaymentMethod method = new PaymentMethod();
        method.setUuid(UUID.randomUUID());
        r.setPaymentMethod(method);
        r.setPaymentAmount(new BigDecimal("25.00"));
        r.setPaymentCurrencyCode("USD");
        r.setPaymentReferenceNumber("REF-123");
        r.setPaymentDate(Instant.parse("2026-10-19T10:00:00Z"));
        r.setPaymentSupportFileUrl("confidential/affiliation_requests/" + r.getUuid() + "/x-comprobante.pdf");
        r.setPaymentSupportFileName("comprobante.pdf");
        r.setPaymentSupportFileContentType("application/pdf");
        r.setPaymentSupportFileSizeBytes(3L);
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        User created = stubApprovalCreation(r, reviewer);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);
        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        PaymentDto paymentDto = mock(PaymentDto.class);
        when(paymentDto.uuid()).thenReturn(payment.getUuid());
        when(paymentsService.register(any(PaymentCreateRequest.class), isNull(), eq(false), eq(reviewer.getUuid())))
                .thenReturn(paymentDto);
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));

        service.approve(r.getUuid(), null, reviewer.getUuid());

        ArgumentCaptor<PaymentCreateRequest> registered = ArgumentCaptor.forClass(PaymentCreateRequest.class);
        verify(paymentsService).register(registered.capture(), isNull(), eq(false), eq(reviewer.getUuid()));
        PaymentCreateRequest p = registered.getValue();
        assertThat(p.membershipUuid()).isEqualTo(r.getMembership().getUuid());
        assertThat(p.amount()).isEqualByComparingTo("25.00");
        assertThat(p.currency()).isEqualTo("USD");
        assertThat(p.paymentMethodUuid()).isEqualTo(method.getUuid());
        assertThat(p.referenceNumber()).isEqualTo("REF-123");
        assertThat(p.inscription()).isTrue();
        assertThat(p.payerUserUuid()).isEqualTo(created.getUuid());
        assertThat(p.adminNotes()).isEqualTo("Pago reportado en la solicitud de afiliación");
        verify(paymentsService).linkSupportFile(payment.getUuid(), r.getPaymentSupportFileUrl(), "comprobante.pdf",
                "application/pdf", 3L);
        assertThat(r.getPayment()).isSameAs(payment);
        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approve_whenThePaymentFails_failsTheApproval() {
        AffiliationRequest r = pending();
        PaymentMethod method = new PaymentMethod();
        method.setUuid(UUID.randomUUID());
        r.setPaymentMethod(method);
        r.setPaymentAmount(new BigDecimal("25.00"));
        r.setPaymentCurrencyCode("USD");
        r.setPaymentDate(Instant.parse("2026-10-19T10:00:00Z"));
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        stubApprovalCreation(r, reviewer);
        when(paymentsService.register(any(PaymentCreateRequest.class), isNull(), eq(false), eq(reviewer.getUuid())))
                .thenThrow(new java.util.NoSuchElementException("payment_method.not_found"));

        assertThatThrownBy(() -> service.approve(r.getUuid(), null, reviewer.getUuid()))
                .hasMessage("payment_method.not_found");
        assertThat(r.getStatus()).isEqualTo("PENDING");
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── Assisted create ────────────────────────────────────────────────────

    private AssistedAffiliationRequestCreateRequest assistedRequest(String password, Boolean accepted) {
        return new AssistedAffiliationRequestCreateRequest("María", "Pérez", "V", "12345678",
                LocalDate.of(1985, 3, 15), " Maria@Example.com ", "0414-1234567", password,
                plan.getUuid(), terms.getUuid(), accepted);
    }

    @Test
    void createAssisted_isAttributedToTheCallingAdvisor_withoutPassword() {
        UUID advisorUserUuid = UUID.randomUUID();
        User advisorUser = new User();
        advisorUser.setUuid(advisorUserUuid);
        Promoter advisor = new Promoter();
        advisor.setDisplayName("Ana Ventas");
        advisor.setReferralCode("ANAV42");
        when(promoterRepository.findActiveByUserUuid(advisorUserUuid)).thenReturn(Optional.of(advisor));
        when(userRepository.findByUuid(advisorUserUuid)).thenReturn(Optional.of(advisorUser));
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(termsVersionService.requireCurrentPublic(TermType.MEMBER, terms.getUuid())).thenReturn(terms);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        AffiliationRequestCreatedDto created = service.createAssisted(assistedRequest(null, true), advisorUserUuid,
                new ClientRequestMetadata("10.0.0.7", "Advisor phone"));

        ArgumentCaptor<AffiliationRequest> saved = ArgumentCaptor.forClass(AffiliationRequest.class);
        verify(repository).save(saved.capture());
        AffiliationRequest r = saved.getValue();
        assertThat(r.getChannel()).isEqualTo("ASSISTED");
        assertThat(r.isAssisted()).isTrue();
        assertThat(r.getPromoter()).isSameAs(advisor);
        assertThat(r.getReferralCode()).isEqualTo("ANAV42");
        assertThat(r.getPasswordHash()).isNull();
        assertThat(r.getCreatedBy()).isEqualTo(advisorUserUuid);
        assertThat(r.getEmail()).isEqualTo("maria@example.com");
        assertThat(r.getTermsRenderedContent()).isEqualTo(CONTRACT);
        assertThat(r.getIpAddress()).isEqualTo("10.0.0.7");
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.advisorName()).isEqualTo("Ana Ventas");
        verify(passwordEncoder, never()).encode(anyString());

        ArgumentCaptor<AffiliationRequestLog> entry = ArgumentCaptor.forClass(AffiliationRequestLog.class);
        verify(logRepository).save(entry.capture());
        assertThat(entry.getValue().getActor()).isSameAs(advisorUser);
        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AffiliationRequestEvent.Kind.RECEIVED);
        assertThat(event.getValue().channel()).isEqualTo("ASSISTED");
        assertThat(event.getValue().advisorName()).isEqualTo("Ana Ventas");
    }

    @Test
    void createAssisted_withPassword_storesItsHash() {
        UUID advisorUserUuid = UUID.randomUUID();
        Promoter advisor = new Promoter();
        advisor.setReferralCode("ANAV42");
        User advisorUser = new User();
        advisorUser.setUuid(advisorUserUuid);
        when(promoterRepository.findActiveByUserUuid(advisorUserUuid)).thenReturn(Optional.of(advisor));
        when(userRepository.findByUuid(advisorUserUuid)).thenReturn(Optional.of(advisorUser));
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(termsVersionService.requireCurrentPublic(TermType.MEMBER, terms.getUuid())).thenReturn(terms);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("$2a$10$hash");

        service.createAssisted(assistedRequest("s3cret-pass", true), advisorUserUuid, ClientRequestMetadata.EMPTY);

        ArgumentCaptor<AffiliationRequest> saved = ArgumentCaptor.forClass(AffiliationRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
    }

    @Test
    void createAssisted_byACallerWithoutAdvisorRecord_is404() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAssisted(assistedRequest(null, true), userUuid, ClientRequestMetadata.EMPTY))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
        verify(repository, never()).save(any());
    }

    @Test
    void createAssisted_withoutTheContractConfirmed_is422() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(new Promoter()));

        assertThatThrownBy(() -> service.createAssisted(assistedRequest(null, false), userUuid, ClientRequestMetadata.EMPTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.contract.not_accepted");
        verify(repository, never()).save(any());
    }

    @Test
    void createAssisted_runsThePublicValidations() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(new Promoter()));
        when(repository.existsByEmailIgnoreCaseAndStatus("maria@example.com", "PENDING")).thenReturn(true);

        assertThatThrownBy(() -> service.createAssisted(assistedRequest(null, true), userUuid, ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation_request.email.pending");
        verify(repository, never()).save(any());
    }

    @Test
    void createAssisted_rejectsAGroupPlan() {
        UUID userUuid = UUID.randomUUID();
        plan.setType(PlanType.CORPORATE);
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(new Promoter()));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(new User()));
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.createAssisted(assistedRequest(null, true), userUuid, ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation.plan.group_only");
        verify(repository, never()).save(any());
    }

    // ─── Reject ─────────────────────────────────────────────────────────────

    @Test
    void reject_withoutReason_isA422Code() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), "  ", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.reject.reason_required");
        verify(repository, never()).findForUpdateByUuid(any());
    }

    @Test
    void reject_storesTheReasonAndEmailsIt() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        service.reject(r.getUuid(), " Documento ilegible ", reviewer.getUuid());

        assertThat(r.getStatus()).isEqualTo("REJECTED");
        assertThat(r.getRejectionReason()).isEqualTo("Documento ilegible");
        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AffiliationRequestEvent.Kind.REJECTED);
        assertThat(event.getValue().reason()).isEqualTo("Documento ilegible");
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
    }

    @Test
    void listForAdvisor_withoutAdvisorRecord_is404() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForAdvisor(userUuid, org.springframework.data.domain.Pageable.unpaged(), null))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
        verify(repository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }
}
