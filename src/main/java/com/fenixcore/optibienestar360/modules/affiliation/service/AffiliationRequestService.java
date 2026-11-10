package com.fenixcore.optibienestar360.modules.affiliation.service;

import com.fenixcore.optibienestar360.common.storage.FileValidationService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.PresignedUrlPolicy;
import com.fenixcore.optibienestar360.common.storage.StorageKeyBuilder;
import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.core.exception.UnprocessableDetailsException;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationDraftStepRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationPaymentRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRegistrationDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestDetailDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListItemDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestLogDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestPaymentDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestUpdateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AssistedAffiliationDraftStartRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AssistedAffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest.Channel;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest.Status;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog.Action;
import com.fenixcore.optibienestar360.modules.affiliation.event.AffiliationRequestEvent;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestLogRepository;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.BeneficiariesService;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipsService;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentSupportUrlDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentsService;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
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
import com.fenixcore.optibienestar360.security.RoleNames;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListFilter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Affiliation requests (V190) — the public self-affiliation reviewed by staff
 * before anything is created.
 *
 * <ol>
 *   <li><b>Create</b> (public form): the same checks the immediate
 *   affiliation used to run — self-service plan, cédula / email not already
 *   registered, valid referral code, current public AFILIADO contract — plus
 *   no other PENDING request with that cédula or email. Stores the data, the
 *   BCrypt hash of the chosen password and the contract evidence (text
 *   rendered with the applicant's data, its sha256, version, IP/User-Agent).</li>
 *   <li><b>Update</b> (reviewer, PENDING only): corrects the data; every edit
 *   is logged with the changed fields.</li>
 *   <li><b>Approve</b>: in one transaction, on the row-locked request —
 *   AFILIADO user with the stored hash, member attributed to the referral's
 *   advisor, membership of the plan forced to SUSPENDED (the validator only
 *   reads the lifecycle status; the first approved payment re-evaluates it).
 *   The contract is re-rendered with the final data: same sha256 → the
 *   applicant's acceptance is recorded ({@code SELF_SERVICE}, their submit
 *   time and IP/User-Agent); different → nothing is recorded and
 *   {@code pendingForUser} asks for the corrected text at first login.</li>
 *   <li><b>Reject</b>: reason required, emailed to the applicant.</li>
 * </ol>
 * The public form may also report the payment of the plan's inscription fee
 * (V226): validated with the payments rules on submit and stored on the request
 * (amount/currency = the plan's inscription fee), its proof uploaded under the
 * {@code affiliation_requests} owner key. Approval registers it as the new
 * membership's inscription payment, which then follows the normal review.
 * Only individually sold plans are accepted (see {@link #requireSelfServicePlan}).
 * A cédula or email that already exists is rejected: attaching a new login to
 * an existing Person would hand a stranger that person's data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AffiliationRequestService {

    static final String ENTITY_KEY = "affiliation_request";

    static final long DEFAULT_ACTIVATION_TTL_HOURS = 72;

    private static final String OWNER_TABLE = "affiliation_requests";
    /** Same visibility as the payments' own proofs ({@code PaymentsService}). */
    private static final FileVisibility SUPPORT_VISIBILITY = FileVisibility.CONFIDENTIAL;

    private static final String[] SEARCHABLE_FIELDS = {
            "firstName", "lastName", "documentNumber", "email", "phone"
    };

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS = sortableFields();

    private final AffiliationRequestRepository repository;
    private final AffiliationRequestLogRepository logRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final PlanRepository planRepository;
    private final UserService userService;
    private final MembersService membersService;
    private final MembershipsService membershipsService;
    private final TermsVersionService termsVersionService;
    private final TermsAcceptanceService termsAcceptanceService;
    private final MemberContractService memberContractService;
    private final PasswordEncoder passwordEncoder;
    private final DefaultSortResolver defaultSortResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthService authService;
    private final PaymentsService paymentsService;
    private final PaymentRepository paymentRepository;
    private final FileValidationService fileValidationService;
    private final ObjectProvider<StorageService> storageProvider;
    private final PresignedUrlPolicy presignedUrlPolicy;
    private final OnboardingPolicyService onboardingPolicyService;
    private final UserRoleRepository userRoleRepository;
    private final BeneficiariesService beneficiariesService;

    /** Hours the "create your password" link of an assisted approval stays valid. */
    @Value("${app.affiliation-requests.activation-ttl-hours:72}")
    private long activationTtlHours = DEFAULT_ACTIVATION_TTL_HOURS;

    /** Log comment of a request approved by the onboarding policy instead of a reviewer. */
    static final String AUTO_APPROVAL_COMMENT = "Aprobada automáticamente por la política de registro";

    // ─── Public form ────────────────────────────────────────────────────────

    @Transactional
    public AffiliationRequestCreatedDto create(AffiliationRequestCreateRequest req, ClientRequestMetadata client) {
        return create(req, null, client);
    }

    /**
     * Public form with an optional proof of the reported inscription payment
     * ({@code support}, the multipart variant). A proof without
     * {@code payment} is a 422 {@code affiliation_request.payment.support_without_payment}.
     */
    @Transactional
    public AffiliationRequestCreatedDto create(AffiliationRequestCreateRequest req, MultipartFile support,
                                               ClientRequestMetadata client) {
        onboardingPolicyService.requireMemberSelfRegistration();
        boolean hasSupport = support != null && !support.isEmpty();
        if (hasSupport && req.payment() == null) {
            throw new IllegalArgumentException("affiliation_request.payment.support_without_payment");
        }
        String code = req.referralCode() == null || req.referralCode().isBlank()
                ? null : req.referralCode().trim().toUpperCase(Locale.ROOT);
        Applicant applicant = new Applicant(req.firstName(), req.lastName(), req.documentType(), req.documentNumber(),
                req.birthDate(), req.email(), req.phone(), req.planUuid(), req.termsVersionUuid());
        validateApplicant(applicant);
        Promoter promoter = code == null ? null : promoterRepository.findByReferralCode(code)
                .orElseThrow(() -> new IllegalArgumentException("affiliation.referral.not_found"));
        return store(applicant, Channel.SELF_SERVICE, promoter, code, passwordEncoder.encode(req.password()),
                client, null, null, null, req.payment(), hasSupport ? support : null);
    }

    /**
     * Assisted affiliation (advisor portal, V194): the advisor fills the form
     * for a family on their own device and the family accepts the contract
     * there. Same checks as {@link #create}; the request is attributed to the
     * caller's active advisor record (404 {@code me.promoter.not_found}
     * otherwise) — its referral code is stored so approval attributes the
     * member the same way — and the password is optional (see
     * {@link #approve}). The CREATED log row carries the advisor as actor.
     */
    @Transactional
    public AffiliationRequestCreatedDto createAssisted(AssistedAffiliationRequestCreateRequest req, UUID advisorUserUuid,
                                                       ClientRequestMetadata client) {
        Promoter advisor = promoterRepository.findActiveByUserUuid(advisorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        if (!Boolean.TRUE.equals(req.contractAccepted())) {
            throw new IllegalArgumentException("affiliation_request.contract.not_accepted");
        }
        Applicant applicant = new Applicant(req.firstName(), req.lastName(), req.documentType(), req.documentNumber(),
                req.birthDate(), req.email(), req.phone(), req.planUuid(), req.termsVersionUuid());
        validateApplicant(applicant);
        String hash = req.password() == null || req.password().isBlank() ? null : passwordEncoder.encode(req.password());
        return store(applicant, Channel.ASSISTED, advisor, advisor.getReferralCode(), hash, client,
                actor(advisorUserUuid), req.expiresAt(), req.reinscriptionRequired(), null, null);
    }

    /** The applicant fields both forms share. */
    private record Applicant(String firstName, String lastName, String documentType, String documentNumber,
                             LocalDate birthDate, String email, String phone, UUID planUuid, UUID termsVersionUuid) {}

    /** Not registered yet, and no other PENDING request with the same cédula / email. */
    private void validateApplicant(Applicant a) {
        String email = normalizeEmail(a.email());
        String documentNumber = a.documentNumber().trim();
        requireNotRegistered(a.documentType(), documentNumber, email);
        if (repository.existsByDocumentTypeAndDocumentNumberAndStatus(a.documentType(), documentNumber, Status.PENDING.name())) {
            throw new IllegalArgumentException("affiliation_request.document.pending");
        }
        if (repository.existsByEmailIgnoreCaseAndStatus(email, Status.PENDING.name())) {
            throw new IllegalArgumentException("affiliation_request.email.pending");
        }
    }

    private AffiliationRequestCreatedDto store(Applicant a, Channel channel, Promoter promoter, String code,
                                               String passwordHash, ClientRequestMetadata client, User actor,
                                               LocalDate expiresAtOverride, Boolean reinscriptionOverride,
                                               AffiliationPaymentRequest payment, MultipartFile support) {
        Plan plan = planRepository.findByUuid(a.planUuid()).orElse(null);
        requireSelfServicePlan(plan);

        TermsVersion terms = termsVersionService.requireCurrentPublic(TermType.MEMBER, a.termsVersionUuid());

        String email = normalizeEmail(a.email());
        AffiliationRequest request = new AffiliationRequest();
        request.setChannel(channel.name());
        request.setFirstName(a.firstName().trim());
        request.setLastName(a.lastName().trim());
        request.setDocumentType(a.documentType());
        request.setDocumentNumber(a.documentNumber().trim());
        request.setBirthDate(a.birthDate());
        request.setEmail(email);
        request.setPhone(blankToNull(a.phone()));
        request.setLocale(LocaleContextHolder.getLocale().getLanguage().equals("en") ? "en" : "es");
        request.setPlan(plan);
        request.setPromoter(promoter);
        request.setReferralCode(code);
        request.setExpiresAtOverride(expiresAtOverride);
        request.setReinscriptionRequiredOverride(reinscriptionOverride);
        request.setPasswordHash(passwordHash);
        request.setTermsVersion(terms);
        String content = memberContractService.render(terms, applicantOf(request));
        request.setTermsRenderedContent(content);
        request.setTermsContentSha256(MemberContractService.sha256(content));
        request.setTermsAcceptedAt(Instant.now());
        ClientRequestMetadata evidence = client != null ? client : ClientRequestMetadata.EMPTY;
        request.setIpAddress(evidence.ipAddress());
        request.setUserAgent(evidence.userAgent());
        request.setStatus(Status.PENDING.name());
        if (actor != null) {
            // Also filled by JPA auditing from the security context; explicit for clarity.
            request.setCreatedBy(actor.getUuid());
        }
        if (payment != null) {
            applyReportedPayment(request, plan, payment);
            storeSupportFile(request, support);
        }
        repository.save(request);

        writeLog(request, Action.CREATED, null, Status.PENDING, null, null, actor);
        eventPublisher.publishEvent(event(AffiliationRequestEvent.Kind.RECEIVED, request, null, null));
        log.info("Affiliation request {} received ({}, referral {})", request.getUuid(), channel, code);
        if (onboardingPolicyService.autoApprovesMember(request.hasReportedPayment())) {
            // Hub ADR 0037 §1: approved by the onboarding policy, no reviewer (actor "Sistema").
            approve(request.getUuid(), AUTO_APPROVAL_COMMENT, null);
        }
        return new AffiliationRequestCreatedDto(request.getUuid(), request.getStatus(), email,
                promoter != null ? promoter.getDisplayName() : null);
    }

    // ─── Reads ──────────────────────────────────────────────────────────────

    /** Admin list, optional {@code status} filter and free-text {@code q}, newest first by default. */
    public Page<AffiliationRequestListItemDto> list(Pageable pageable, String status, String q) {
        return list(pageable, status, q, AffiliationRequestListFilter.none());
    }

    public Page<AffiliationRequestListItemDto> list(Pageable pageable, String status, String q,
                                                    AffiliationRequestListFilter filters) {
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);
        Specification<AffiliationRequest> spec = withStatus(parseStatus(status));
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (filters != null) {
            spec = spec.and(masterFilters(filters));
        }
        return repository.findAll(spec, resolved).map(this::toListItem);
    }

    /** AND of the master-table filters provided; nullable associations use explicit LEFT JOINs. */
    private static Specification<AffiliationRequest> masterFilters(AffiliationRequestListFilter f) {
        java.time.ZoneId zone = java.time.ZoneId.of("America/Caracas");
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new java.util.ArrayList<>();
            if (f.planUuid() != null) {
                ps.add(cb.equal(root.join("plan", jakarta.persistence.criteria.JoinType.LEFT).get("uuid"), f.planUuid()));
            }
            if (f.promoterUuid() != null) {
                ps.add(cb.equal(root.join("promoter", jakarta.persistence.criteria.JoinType.LEFT).get("uuid"), f.promoterUuid()));
            }
            if (f.paymentMethodUuid() != null) {
                ps.add(cb.equal(root.join("paymentMethod", jakarta.persistence.criteria.JoinType.LEFT).get("uuid"), f.paymentMethodUuid()));
            }
            if (f.dateFrom() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"),
                        f.dateFrom().atStartOfDay(zone).toInstant()));
            }
            if (f.dateTo() != null) {
                ps.add(cb.lessThan(root.<java.time.Instant>get("createdAt"),
                        f.dateTo().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort(ENTITY_KEY, pageable);
    }

    /**
     * Advisor portal: the requests that came through the caller's own QR
     * (their active advisor record). 404 when the caller is not an advisor.
     */
    public Page<AffiliationRequestListItemDto> listForAdvisor(UUID userUuid, Pageable pageable, String status) {
        return listForAdvisor(userUuid, pageable, status, null);
    }

    /** Same as above plus a free-text {@code q} (accent/case-insensitive) over the applicant's name, document, email and phone. */
    public Page<AffiliationRequestListItemDto> listForAdvisor(UUID userUuid, Pageable pageable, String status, String q) {
        return listForAdvisor(userUuid, pageable, status, q, null, null);
    }

    /** Adds an inclusive creation-day range ({@code dateFrom}/{@code dateTo}, Caracas days) on {@code createdAt}. */
    public Page<AffiliationRequestListItemDto> listForAdvisor(UUID userUuid, Pageable pageable, String status, String q,
                                                              java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        Promoter advisor = promoterRepository.findActiveByUserUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);
        Long advisorId = advisor.getId();
        Specification<AffiliationRequest> spec = withStatus(parseStatus(status))
                .and((root, query, cb) -> cb.equal(root.get("promoter").get("id"), advisorId));
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        spec = spec.and(com.fenixcore.optibienestar360.core.util.DateRangeSpecifications.instantBetween("createdAt", dateFrom, dateTo));
        return repository.findAll(spec, resolved).map(this::toListItem);
    }

    public AffiliationRequestDetailDto get(UUID uuid) {
        return toDetail(repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found")));
    }

    // ─── Review ─────────────────────────────────────────────────────────────

    /** Corrects a PENDING request; logs the changed fields (nothing changed → no log row). */
    @Transactional
    public AffiliationRequestDetailDto update(UUID uuid, AffiliationRequestUpdateRequest req, UUID actorUuid) {
        AffiliationRequest request = lockPending(uuid, "affiliation_request.update.invalid_state");
        String email = normalizeEmail(req.email());
        String documentNumber = req.documentNumber().trim();

        boolean documentChanged = !req.documentType().equals(request.getDocumentType())
                || !documentNumber.equals(request.getDocumentNumber());
        boolean emailChanged = !email.equals(request.getEmail());
        if (documentChanged && personRepository.existsByDocumentTypeAndDocumentNumber(req.documentType(), documentNumber)) {
            throw new IllegalArgumentException("affiliation.document.exists");
        }
        if (emailChanged && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("user.email.exists");
        }
        if (documentChanged && repository.existsByDocumentTypeAndDocumentNumberAndStatusAndIdNot(
                req.documentType(), documentNumber, Status.PENDING.name(), request.getId())) {
            throw new IllegalArgumentException("affiliation_request.document.pending");
        }
        if (emailChanged && repository.existsByEmailIgnoreCaseAndStatusAndIdNot(
                email, Status.PENDING.name(), request.getId())) {
            throw new IllegalArgumentException("affiliation_request.email.pending");
        }
        Plan plan = request.getPlan();
        if (!Objects.equals(req.planUuid(), plan.getUuid())) {
            plan = planRepository.findByUuid(req.planUuid()).orElse(null);
            requireSelfServicePlan(plan);
        }

        List<Map<String, String>> changes = new ArrayList<>();
        track(changes, "firstName", request.getFirstName(), req.firstName().trim());
        track(changes, "lastName", request.getLastName(), req.lastName().trim());
        track(changes, "documentType", request.getDocumentType(), req.documentType());
        track(changes, "documentNumber", request.getDocumentNumber(), documentNumber);
        track(changes, "birthDate", str(request.getBirthDate()), str(req.birthDate()));
        track(changes, "email", request.getEmail(), email);
        track(changes, "phone", request.getPhone(), blankToNull(req.phone()));
        track(changes, "plan", request.getPlan().getName(), plan.getName());
        track(changes, "expiresAt", str(request.getExpiresAtOverride()), str(req.expiresAt()));
        track(changes, "reinscriptionRequired", Objects.toString(request.getReinscriptionRequiredOverride(), null), Objects.toString(req.reinscriptionRequired(), null));
        if (changes.isEmpty()) {
            return toDetail(request);
        }

        request.setFirstName(req.firstName().trim());
        request.setLastName(req.lastName().trim());
        request.setDocumentType(req.documentType());
        request.setDocumentNumber(documentNumber);
        request.setBirthDate(req.birthDate());
        request.setEmail(email);
        request.setPhone(blankToNull(req.phone()));
        request.setPlan(plan);
        request.setExpiresAtOverride(req.expiresAt());
        request.setReinscriptionRequiredOverride(req.reinscriptionRequired());

        writeLog(request, Action.UPDATED, null, null, changes, blankToNull(req.comment()), actor(actorUuid));
        return toDetail(request);
    }

    /**
     * Creates the user, the member and the SUSPENDED membership from a
     * PENDING request (see the class javadoc). The request row is locked for
     * the whole transaction, so a double click or two reviewers can't create
     * two users: the second call finds it APPROVED and fails with a 422.
     */
    @Transactional
    public AffiliationRequestDetailDto approve(UUID uuid, String comment, UUID actorUuid) {
        AffiliationRequest request = lockPending(uuid, "affiliation_request.approve.invalid_state");
        User reviewer = actor(actorUuid);
        // A DRAFT-born request already owns its login (step 1 created it with the pending role).
        boolean hasLogin = request.getUser() != null;
        if (!hasLogin) {
            // Someone may have registered the same cédula/email since the request arrived.
            requireNotRegistered(request.getDocumentType(), request.getDocumentNumber(), request.getEmail());
        }
        requireSelfServicePlan(request.getPlan());

        Role memberRole = roleRepository.findByName(RoleNames.MEMBER)
                .orElseThrow(() -> new IllegalStateException("role " + RoleNames.MEMBER + " missing"));

        // Assisted request without password: an unusable random one until the
        // member sets theirs through the activation link in the approval email.
        boolean passwordPending = !hasLogin && request.getPasswordHash() == null;
        UserDto createdUser = null;
        if (hasLogin) {
            swapPendingRole(request.getUser(), memberRole, actorUuid);
        } else {
            String passwordHash = passwordPending
                    // 64 hex chars: BCrypt rejects inputs over 72 bytes
                    ? passwordEncoder.encode(UUID.randomUUID().toString().replace("-", "")
                            + UUID.randomUUID().toString().replace("-", ""))
                    : request.getPasswordHash();
            createdUser = userService.createUserWithPasswordHash(new AdminCreateUserRequest(
                    request.getEmail(), request.getFirstName(), null, request.getLastName(), null, null,
                    request.getDocumentType(), request.getDocumentNumber(), null, null, request.getPhone(),
                    List.of(memberRole.getUuid()), memberRole.getUuid()), passwordHash, actorUuid);
        }

        String code = request.getReferralCode();
        String origin = request.isAssisted()
                ? "Afiliación asistida por el asesor"
                        + (request.getPromoter() != null ? " " + request.getPromoter().getDisplayName() : "")
                : "Autoafiliación desde la página pública";
        MemberDetailDto memberDto = membersService.create(new MemberCreateRequest(
                request.getFirstName(), null, request.getLastName(), null,
                request.getDocumentType(), request.getDocumentNumber(), null, null,
                request.getBirthDate(), null, null, null, null, null,
                request.getPhone(), null, request.getEmail(), request.getLocale(), null, null, null, null, null, null,
                null, origin + (code != null ? " · código " + code : ""),
                code));

        MembershipDto enrolled = membershipsService.enroll(memberDto.uuid(),
                new MembershipCreateRequest(request.getPlan().getUuid(), null, request.getExpiresAtOverride(), null, null,
                        request.getReinscriptionRequiredOverride()), actorUuid);
        Membership membership = membershipRepository.findByUuid(enrolled.uuid())
                .orElseThrow(() -> new IllegalStateException("membership just created not found"));
        // Hub ADR 0037 §1: SUSPENDED until the payment is approved, unless the
        // onboarding policy activates it on approval (or on a reported payment).
        membership.setStatus(onboardingPolicyService.activatesMembershipOnApproval(request.hasReportedPayment())
                ? Membership.LifecycleStatus.ACTIVE.name()
                : Membership.LifecycleStatus.SUSPENDED.name());

        User user = hasLogin ? request.getUser()
                : userRepository.findByUuid(createdUser.uuid())
                        .orElseThrow(() -> new IllegalStateException("user just created not found"));
        Member member = memberRepository.findByUuid(memberDto.uuid())
                .orElseThrow(() -> new IllegalStateException("member just created not found"));
        if (request.hasReportedPayment()) {
            request.setPayment(registerReportedPayment(request, membership, user, actorUuid));
        }

        addFamily(request, memberDto.uuid());

        boolean termsUnchanged = !termsChanged(request);
        if (termsUnchanged) {
            termsAcceptanceService.recordFormAcceptance(user, request.getTermsVersion(), membership,
                    new ClientRequestMetadata(request.getIpAddress(), request.getUserAgent()),
                    request.getTermsAcceptedAt(), request.getTermsRenderedContent(),
                    request.isAssisted() ? TermsAcceptance.Channel.ASSISTED : TermsAcceptance.Channel.SELF_SERVICE);
        }
        String activationUrl = passwordPending
                ? authService.issuePasswordSetupLink(user, activationTtl(), Locale.forLanguageTag(
                        request.getLocale() != null ? request.getLocale() : "es"))
                : null;

        request.setUser(user);
        request.setMember(member);
        request.setMembership(membership);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(Instant.now());
        request.setStatus(Status.APPROVED.name());

        writeLog(request, Action.APPROVED, Status.PENDING, Status.APPROVED, null, blankToNull(comment), reviewer);
        eventPublisher.publishEvent(event(AffiliationRequestEvent.Kind.APPROVED, request, null, activationUrl));
        log.info("Affiliation request {} approved → member {} (terms acceptance {})", request.getUuid(),
                member.getUuid(), termsUnchanged ? "recorded" : "pending at first login");
        return toDetail(request);
    }

    /** Rejects a PENDING request; {@code reason} is required (422) and emailed to the applicant. */
    @Transactional
    public AffiliationRequestDetailDto reject(UUID uuid, String reason, UUID actorUuid) {
        String finalReason = blankToNull(reason);
        if (finalReason == null) {
            throw new IllegalArgumentException("affiliation_request.reject.reason_required");
        }
        AffiliationRequest request = lockPending(uuid, "affiliation_request.reject.invalid_state");
        User reviewer = actor(actorUuid);
        request.setRejectionReason(finalReason);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(Instant.now());
        request.setStatus(Status.REJECTED.name());

        writeLog(request, Action.REJECTED, Status.PENDING, Status.REJECTED, null, finalReason, reviewer);
        eventPublisher.publishEvent(event(AffiliationRequestEvent.Kind.REJECTED, request, finalReason, null));
        return toDetail(request);
    }

    // ─── Reported inscription payment ───────────────────────────────────────

    /**
     * Short-lived presigned URL of the proof attached to the reported payment;
     * same shape and TTL clamp as {@code GET /v1/admin/payments/{uuid}/support}.
     * 404 {@code payment.support.not_available} when no proof was stored in R2.
     */
    public PaymentSupportUrlDto paymentSupportUrl(UUID uuid, Duration requestedTtl) {
        AffiliationRequest request = repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        String key = request.getPaymentSupportFileUrl();
        if (key == null || key.isBlank()) {
            throw new NoSuchElementException("payment.support.not_available");
        }
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalArgumentException("payment.support.storage_unavailable");
        }
        Duration ttl = presignedUrlPolicy.clamp(requestedTtl);
        return new PaymentSupportUrlDto(storage.generatePresignedUrl(key, ttl), Instant.now().plus(ttl),
                ttl.toSeconds(), request.getPaymentSupportFileName(), request.getPaymentSupportFileContentType(),
                request.getPaymentSupportFileSizeBytes());
    }

    /**
     * Validates the reported payment with the payments rules (method active +
     * its mandatory fields, bank active) and stores it on the request. Amount
     * and currency are the plan's inscription fee; a plan without one has
     * nothing to pay ({@code affiliation_request.payment.no_inscription_fee}).
     */
    private void applyReportedPayment(AffiliationRequest request, Plan plan, AffiliationPaymentRequest p) {
        PaymentsService.MethodSelection selection = paymentsService.resolveMethodSelection(p.paymentMethodUuid(),
                p.bankUuid(), p.referenceNumber(), p.phone(), p.email(), p.bankAccountIdentifier());
        // A direct-receipt method (cash) is auto-approved on registration: an
        // anonymous applicant claiming one would get an active membership and a
        // commission for money nobody received. Only reviewable methods here.
        if (!selection.method().isRequiresApproval()) {
            throw new IllegalArgumentException("affiliation_request.payment.method_not_allowed");
        }
        if (plan.getInscriptionFee() == null || plan.getInscriptionFee().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("affiliation_request.payment.no_inscription_fee");
        }
        request.setPaymentMethod(selection.method());
        request.setPaymentBank(selection.bank());
        request.setPaymentAmount(plan.getInscriptionFee());
        request.setPaymentCurrencyCode(plan.getCurrency() != null ? plan.getCurrency().getCode() : null);
        request.setPaymentIdentification(blankToNull(p.identification()));
        request.setPaymentBankAccountType(blankToNull(p.bankAccountType()));
        request.setPaymentBankAccountCode(blankToNull(p.bankAccountCode()));
        request.setPaymentBankAccountIdentifier(blankToNull(p.bankAccountIdentifier()));
        request.setPaymentPhone(blankToNull(p.phone()));
        request.setPaymentEmail(blankToNull(p.email()));
        request.setPaymentReferenceNumber(blankToNull(p.referenceNumber()));
        request.setPaymentDate(p.paymentDate());
    }

    /**
     * Same validation and storage as a payment's own proof
     * ({@code PaymentsService.attachSupportFile}), keyed under the request:
     * without R2 only the metadata is kept.
     */
    private void storeSupportFile(AffiliationRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        fileValidationService.validate(SUPPORT_VISIBILITY, file);
        request.setPaymentSupportFileName(file.getOriginalFilename());
        request.setPaymentSupportFileContentType(FileValidationService.safeContentType(file.getOriginalFilename()));
        request.setPaymentSupportFileSizeBytes(file.getSize());

        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.info("R2 storage disabled — affiliation request support file metadata captured but bytes discarded");
            return;
        }
        // @PrePersist assigns the UUID only at flush time; the owner key needs it now.
        if (request.getUuid() == null) {
            request.setUuid(UUID.randomUUID());
        }
        String key = StorageKeyBuilder.build(SUPPORT_VISIBILITY, OWNER_TABLE, request.getUuid(),
                file.getOriginalFilename());
        try {
            storage.upload(key, file.getInputStream(), file.getSize(), FileValidationService.safeContentType(file.getOriginalFilename()));
            request.setPaymentSupportFileUrl(key);
        } catch (IOException ex) {
            log.error("Failed to read support file stream for affiliation request", ex);
            throw new IllegalArgumentException("payment.support.upload_failed");
        }
    }

    /**
     * Registers the reported payment as the new membership's inscription
     * payment through the normal payments flow (PENDING review, or
     * auto-approved for a direct-receipt method) and links the stored proof
     * to it. Any payments error propagates and rolls the approval back.
     */
    private Payment registerReportedPayment(AffiliationRequest request, Membership membership, User payer,
                                            UUID actorUuid) {
        PaymentDto created = paymentsService.register(new PaymentCreateRequest(
                membership.getUuid(), request.getPaymentAmount(), request.getPaymentCurrencyCode(),
                request.getPaymentMethod().getUuid(),
                request.getPaymentBank() != null ? request.getPaymentBank().getUuid() : null,
                request.getPaymentIdentification(), request.getPaymentBankAccountType(),
                request.getPaymentBankAccountCode(), request.getPaymentBankAccountIdentifier(),
                request.getPaymentPhone(), request.getPaymentEmail(), request.getPaymentReferenceNumber(),
                request.getPaymentDate(), true, null, null, payer.getUuid(),
                "Pago reportado en la solicitud de afiliación", null), null, false, actorUuid);
        if (request.getPaymentSupportFileName() != null) {
            paymentsService.linkSupportFile(created.uuid(), request.getPaymentSupportFileUrl(),
                    request.getPaymentSupportFileName(), request.getPaymentSupportFileContentType(),
                    request.getPaymentSupportFileSizeBytes());
        }
        return paymentRepository.findByUuid(created.uuid())
                .orElseThrow(() -> new IllegalStateException("payment just created not found"));
    }

    // ─── Step-by-step registration (hub ADR 0037 §2-3) ───────────────────────

    private static final List<String> STEPS = List.of("personal", "plan", "payment", "family");

    /**
     * Step 1 of {@code /afiliarse}: creates the person, the login (role
     * {@code AFILIADO_PENDIENTE}; the password is optional) and the DRAFT
     * request, and opens the session so the person keeps going without
     * signing in. A cédula or email that already has an account is a 422
     * {@code ACCOUNT_EXISTS}.
     */
    @Transactional
    public AffiliationRequestCreatedDto startRegistration(AffiliationRequestCreateRequest req,
                                                          ClientRequestMetadata client,
                                                          HttpServletRequest httpRequest) {
        onboardingPolicyService.requireMemberSelfRegistration();
        String email = normalizeEmail(req.email());
        String documentNumber = req.documentNumber().trim();
        if (personRepository.existsByDocumentTypeAndDocumentNumber(req.documentType(), documentNumber)
                || userRepository.existsByEmail(email)) {
            throw new UnprocessableDetailsException("affiliation_request.account_exists", null,
                    Map.of("code", "ACCOUNT_EXISTS"));
        }
        String code = req.referralCode() == null || req.referralCode().isBlank()
                ? null : req.referralCode().trim().toUpperCase(Locale.ROOT);
        Promoter promoter = code == null ? null : promoterRepository.findByReferralCode(code)
                .orElseThrow(() -> new IllegalArgumentException("affiliation.referral.not_found"));
        Role pendingRole = roleRepository.findByName(RoleNames.MEMBER_PENDING)
                .orElseThrow(() -> new IllegalStateException("role " + RoleNames.MEMBER_PENDING + " missing"));

        // No password: an unusable random one (64 hex chars, BCrypt caps at 72 bytes); the person signs in with an access link.
        String rawPassword = req.password() == null || req.password().isBlank()
                ? UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
                : req.password();
        UserDto created = userService.createUserWithPasswordHash(new AdminCreateUserRequest(
                email, req.firstName().trim(), null, req.lastName().trim(), null, null,
                req.documentType(), documentNumber, null, null, blankToNull(req.phone()),
                List.of(pendingRole.getUuid()), pendingRole.getUuid()), passwordEncoder.encode(rawPassword), null);
        User user = userRepository.findByUuid(created.uuid())
                .orElseThrow(() -> new IllegalStateException("user just created not found"));

        ClientRequestMetadata evidence = client != null ? client : ClientRequestMetadata.EMPTY;
        AffiliationRequest request = new AffiliationRequest();
        request.setChannel(Channel.SELF_SERVICE.name());
        request.setFirstName(req.firstName().trim());
        request.setLastName(req.lastName().trim());
        request.setDocumentType(req.documentType());
        request.setDocumentNumber(documentNumber);
        request.setEmail(email);
        request.setPhone(blankToNull(req.phone()));
        request.setLocale(LocaleContextHolder.getLocale().getLanguage().equals("en") ? "en" : "es");
        request.setPromoter(promoter);
        request.setReferralCode(code);
        request.setUser(user);
        request.setIpAddress(evidence.ipAddress());
        request.setUserAgent(evidence.userAgent());
        request.setStatus(Status.DRAFT.name());
        repository.save(request);
        writeLog(request, Action.CREATED, null, Status.DRAFT, null, null, null);
        log.info("Affiliation registration {} started (DRAFT, referral {})", request.getUuid(), code);

        LoginResponse session = authService.openSession(withRoles(user, pendingRole), httpRequest);
        return new AffiliationRequestCreatedDto(request.getUuid(), request.getStatus(), email,
                promoter != null ? promoter.getDisplayName() : null, session);
    }

    /** The caller's latest registration; 404 {@code affiliation_request.not_found} when they have none. */
    public AffiliationRegistrationDto getMine(UUID userUuid) {
        return toRegistration(repository.findFirstByUser_UuidOrderByCreatedAtDesc(userUuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found")));
    }

    /**
     * Saves one step of the caller's DRAFT ({@code personal}, {@code plan},
     * {@code payment} or {@code family}); each step reads only its own fields
     * (see {@link AffiliationDraftStepRequest}). {@code support} is the proof of
     * the payment step.
     */
    @Transactional
    public AffiliationRegistrationDto saveStep(UUID userUuid, String step, AffiliationDraftStepRequest body,
                                               MultipartFile support, ClientRequestMetadata client) {
        String key = stepKey(step);
        return toRegistration(applyStep(lockOwnDraft(userUuid), key, body, support, client));
    }

    private static String stepKey(String step) {
        String key = step == null ? "" : step.trim().toLowerCase(Locale.ROOT);
        if (!STEPS.contains(key)) {
            throw new IllegalArgumentException("affiliation_request.step.invalid");
        }
        return key;
    }

    /** Dispatches one step into a locked DRAFT; shared by the person's own flow and the advisor's assisted one. */
    private AffiliationRequest applyStep(AffiliationRequest request, String key, AffiliationDraftStepRequest body,
                                         MultipartFile support, ClientRequestMetadata client) {
        AffiliationDraftStepRequest b = body != null
                ? body : new AffiliationDraftStepRequest(null, null, null, null, null, null, null, null, null, null, null, null);
        switch (key) {
            case "personal" -> savePersonalStep(request, b);
            case "plan" -> savePlanStep(request, b, client);
            case "payment" -> savePaymentStep(request, b, support);
            default -> saveFamilyStep(request, b);
        }
        return request;
    }

    private void savePersonalStep(AffiliationRequest request, AffiliationDraftStepRequest b) {
        // An assisted draft may have no login yet (the person is created on approval): then
        // only the request row holds the data, and the advisor can still correct the identity.
        Person person = request.getUser() != null ? request.getUser().getPerson() : null;
        if (request.getUser() == null) {
            saveAssistedIdentity(request, b);
        }
        if (b.firstName() != null && !b.firstName().isBlank()) {
            request.setFirstName(b.firstName().trim());
            if (person != null) person.setFirstName(b.firstName().trim());
        }
        if (b.lastName() != null && !b.lastName().isBlank()) {
            request.setLastName(b.lastName().trim());
            if (person != null) person.setLastName(b.lastName().trim());
        }
        if (b.birthDate() != null) {
            request.setBirthDate(b.birthDate());
            if (person != null) person.setBirthDate(b.birthDate());
        }
        if (b.phone() != null) {
            request.setPhone(blankToNull(b.phone()));
            if (person != null) person.setPhone(blankToNull(b.phone()));
        }
        // A changed name or birth date changes the contract text: termsChanged() then
        // reports it and the acceptance is asked for again before submitting.
    }

    /** Corrects the cédula / email of an assisted draft that has no login; the same checks as when it started. */
    private void saveAssistedIdentity(AffiliationRequest request, AffiliationDraftStepRequest b) {
        String documentType = b.documentType() != null && !b.documentType().isBlank()
                ? b.documentType().trim() : request.getDocumentType();
        String documentNumber = b.documentNumber() != null && !b.documentNumber().isBlank()
                ? b.documentNumber().trim() : request.getDocumentNumber();
        String email = b.email() != null && !b.email().isBlank() ? normalizeEmail(b.email()) : request.getEmail();
        boolean documentChanged = !documentType.equals(request.getDocumentType())
                || !documentNumber.equals(request.getDocumentNumber());
        boolean emailChanged = !email.equals(request.getEmail());
        if (documentChanged || emailChanged) {
            requireNotRegistered(documentType, documentNumber, email);
            requireNoOpenRequest(documentType, documentNumber, email, request.getId(), Status.PENDING, Status.DRAFT);
        }
        request.setDocumentType(documentType);
        request.setDocumentNumber(documentNumber);
        request.setEmail(email);
    }

    /**
     * No other request in the given statuses with the same cédula / email. Starting or correcting a
     * draft checks PENDING and DRAFT; submitting checks only PENDING (the database also keeps
     * those unique), so two drafts of the same person never block each other's submit.
     */
    private void requireNoOpenRequest(String documentType, String documentNumber, String email, Long selfId,
                                      Status... statuses) {
        long id = selfId != null ? selfId : -1L;
        for (Status open : statuses) {
            String suffix = open == Status.PENDING ? "pending" : "draft";
            if (repository.existsByDocumentTypeAndDocumentNumberAndStatusAndIdNot(documentType, documentNumber, open.name(), id)) {
                throw new IllegalArgumentException("affiliation_request.document." + suffix);
            }
            if (repository.existsByEmailIgnoreCaseAndStatusAndIdNot(email, open.name(), id)) {
                throw new IllegalArgumentException("affiliation_request.email." + suffix);
            }
        }
    }

    private void savePlanStep(AffiliationRequest request, AffiliationDraftStepRequest b, ClientRequestMetadata client) {
        if (b.planUuid() == null || b.termsVersionUuid() == null) {
            throw new IllegalArgumentException("affiliation_request.plan.required");
        }
        if (!Boolean.TRUE.equals(b.contractAccepted())) {
            throw new IllegalArgumentException("affiliation_request.contract.not_accepted");
        }
        if (request.getBirthDate() == null) {
            throw new IllegalArgumentException("affiliation_request.personal.required");
        }
        Plan plan = planRepository.findByUuid(b.planUuid()).orElse(null);
        requireSelfServicePlan(plan);
        TermsVersion terms = termsVersionService.requireCurrentPublic(TermType.MEMBER, b.termsVersionUuid());
        boolean planChanged = request.getPlan() == null || !request.getPlan().getId().equals(plan.getId());
        request.setPlan(plan);
        request.setTermsVersion(terms);
        String content = memberContractService.render(terms, applicantOf(request));
        request.setTermsRenderedContent(content);
        request.setTermsContentSha256(MemberContractService.sha256(content));
        request.setTermsAcceptedAt(Instant.now());
        ClientRequestMetadata evidence = client != null ? client : ClientRequestMetadata.EMPTY;
        request.setIpAddress(evidence.ipAddress());
        request.setUserAgent(evidence.userAgent());
        if (planChanged) {
            clearReportedPayment(request);  // the amount was the previous plan's inscription fee
        }
    }

    private void savePaymentStep(AffiliationRequest request, AffiliationDraftStepRequest b, MultipartFile support) {
        if (request.getPlan() == null) {
            throw new IllegalArgumentException("affiliation_request.plan.required");
        }
        boolean hasSupport = support != null && !support.isEmpty();
        if (b.payment() == null) {
            if (hasSupport) {
                throw new IllegalArgumentException("affiliation_request.payment.support_without_payment");
            }
            clearReportedPayment(request);  // the step was skipped or the report withdrawn
            return;
        }
        applyReportedPayment(request, request.getPlan(), b.payment());
        storeSupportFile(request, hasSupport ? support : null);
    }

    private void saveFamilyStep(AffiliationRequest request, AffiliationDraftStepRequest b) {
        List<BeneficiaryCreateRequest> family = b.family() != null ? b.family() : List.of();
        Plan plan = request.getPlan();
        if (plan != null && plan.getMaxBeneficiaries() != null && family.size() > plan.getMaxBeneficiaries()) {
            throw new IllegalArgumentException("member.beneficiary.cap_exceeded");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Map<String, String>> stored = new ArrayList<>();
        for (BeneficiaryCreateRequest f : family) {
            String id = f.documentType() + f.documentNumber().trim();
            if (id.equals(request.getDocumentType() + request.getDocumentNumber())) {
                throw new IllegalArgumentException("affiliation_request.family.is_applicant");
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("affiliation_request.family.duplicate");
            }
            Map<String, String> m = new LinkedHashMap<>();
            m.put("firstName", f.firstName().trim());
            m.put("lastName", f.lastName().trim());
            m.put("documentType", f.documentType());
            m.put("documentNumber", f.documentNumber().trim());
            m.put("birthDate", str(f.birthDate()));
            m.put("relationship", f.relationship().name());
            m.put("phone", blankToNull(f.phone()));
            m.put("email", blankToNull(f.email()));
            stored.add(m);
        }
        request.setFamily(stored.isEmpty() ? null : stored);
    }

    /**
     * Sends the DRAFT to review: needs the essentials (birth date, plan and the
     * contract accepted with the current data), moves it to PENDING and applies
     * the onboarding policy — a policy that approves on submit reuses
     * {@link #approve} with the system actor.
     */
    @Transactional
    public AffiliationRegistrationDto submit(UUID userUuid) {
        AffiliationRequest request = lockOwnDraft(userUuid);
        return submitDraft(request, request.getUser());
    }

    /** DRAFT → PENDING for a locked draft; {@code actor} is who sent it (the person, or the advisor of an assisted one). */
    private AffiliationRegistrationDto submitDraft(AffiliationRequest request, User actor) {
        List<String> missing = new ArrayList<>();
        if (request.getBirthDate() == null) missing.add("birthDate");
        if (request.getPlan() == null) missing.add("plan");
        if (request.getTermsVersion() == null || request.getTermsAcceptedAt() == null || termsChanged(request)) {
            missing.add("contract");
        }
        if (!missing.isEmpty()) {
            throw new UnprocessableDetailsException("affiliation_request.submit.incomplete", null,
                    Map.of("missing", missing));
        }
        request.setStatus(Status.PENDING.name());
        request.setSubmittedAt(Instant.now());
        writeLog(request, Action.SUBMITTED, Status.DRAFT, Status.PENDING, null, null, actor);
        if (onboardingPolicyService.autoApprovesMember(request.hasReportedPayment())) {
            // Hub ADR 0037 §1: approved by the onboarding policy, no reviewer (actor "Sistema").
            // The approval email replaces the "received" one.
            approve(request.getUuid(), AUTO_APPROVAL_COMMENT, null);
        } else {
            eventPublisher.publishEvent(event(AffiliationRequestEvent.Kind.RECEIVED, request, null, null));
        }
        log.info("Affiliation registration {} submitted → {}", request.getUuid(), request.getStatus());
        return toRegistration(request);
    }

    // ─── Assisted drafts (advisor portal, hub ADR 0037 §5) ───────────────────

    /**
     * Starts an assisted affiliation as a DRAFT the advisor completes step by
     * step and can resume later. Unlike the person's own step 1 it creates no
     * login: the person is created on approval, exactly as with the one-shot
     * assisted request. Not blocked by the self-registration switch (it is not
     * self-registration). The DRAFT is attributed to the caller's active advisor
     * record; 404 {@code me.promoter.not_found} otherwise.
     */
    @Transactional
    public AffiliationRegistrationDto startAssistedDraft(AssistedAffiliationDraftStartRequest req, UUID advisorUserUuid,
                                                         ClientRequestMetadata client) {
        Promoter advisor = requireAdvisor(advisorUserUuid);
        String email = normalizeEmail(req.email());
        String documentNumber = req.documentNumber().trim();
        requireNotRegistered(req.documentType(), documentNumber, email);
        requireNoOpenRequest(req.documentType(), documentNumber, email, null, Status.PENDING, Status.DRAFT);

        User actor = actor(advisorUserUuid);
        ClientRequestMetadata evidence = client != null ? client : ClientRequestMetadata.EMPTY;
        AffiliationRequest request = new AffiliationRequest();
        request.setChannel(Channel.ASSISTED.name());
        request.setFirstName(req.firstName().trim());
        request.setLastName(req.lastName().trim());
        request.setDocumentType(req.documentType());
        request.setDocumentNumber(documentNumber);
        request.setEmail(email);
        request.setPhone(blankToNull(req.phone()));
        request.setLocale(LocaleContextHolder.getLocale().getLanguage().equals("en") ? "en" : "es");
        request.setPromoter(advisor);
        request.setReferralCode(advisor.getReferralCode());
        request.setIpAddress(evidence.ipAddress());
        request.setUserAgent(evidence.userAgent());
        request.setStatus(Status.DRAFT.name());
        request.setCreatedBy(actor.getUuid());
        repository.save(request);
        writeLog(request, Action.CREATED, null, Status.DRAFT, null, null, actor);
        log.info("Assisted affiliation draft {} started by advisor {}", request.getUuid(), advisor.getReferralCode());
        return toRegistration(request);
    }

    /** The advisor's own assisted drafts (not the self-service ones of their QR), newest first. */
    @Transactional(readOnly = true)
    public Page<AffiliationRequestListItemDto> listAssistedDrafts(UUID advisorUserUuid, Pageable pageable) {
        Promoter advisor = requireAdvisor(advisorUserUuid);
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);
        Long advisorId = advisor.getId();
        Specification<AffiliationRequest> spec = withStatus(Status.DRAFT)
                .and((root, query, cb) -> cb.equal(root.get("promoter").get("id"), advisorId))
                .and((root, query, cb) -> cb.equal(root.get("channel"), Channel.ASSISTED.name()));
        return repository.findAll(spec, resolved).map(this::toListItem);
    }

    /**
     * The registrations in progress an advisor may find to keep them out of the abandoned-draft cleanup: their own
     * (assisted, or self-registered through their QR) and the self-registered ones nobody is attributed to. Another
     * advisor's never shows.
     */
    @Transactional(readOnly = true)
    public Page<AffiliationRequestListItemDto> listInProgressForAdvisor(UUID advisorUserUuid, Pageable pageable) {
        Promoter advisor = requireAdvisor(advisorUserUuid);
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);
        Long advisorId = advisor.getId();
        Specification<AffiliationRequest> spec = withStatus(Status.DRAFT)
                .and((root, query, cb) -> cb.or(
                        cb.isNull(root.get("promoter")),
                        cb.equal(root.get("promoter").get("id"), advisorId)));
        return repository.findAll(spec, resolved).map(this::toListItem);
    }

    /** What is saved so far in one of the caller's assisted drafts, to resume it. */
    @Transactional(readOnly = true)
    public AffiliationRegistrationDto getAssistedDraft(UUID advisorUserUuid, UUID uuid) {
        Promoter advisor = requireAdvisor(advisorUserUuid);
        AffiliationRequest request = repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        requireOwnAssisted(request, advisor);
        return toRegistration(request);
    }

    /** Saves one step ({@code personal | plan | payment | family}) of the caller's assisted draft. */
    @Transactional
    public AffiliationRegistrationDto saveAssistedStep(UUID advisorUserUuid, UUID uuid, String step,
                                                       AffiliationDraftStepRequest body, MultipartFile support,
                                                       ClientRequestMetadata client) {
        String key = stepKey(step);
        return toRegistration(applyStep(lockAssistedDraft(advisorUserUuid, uuid), key, body, support, client));
    }

    /**
     * Sends the caller's assisted draft to review: the same essentials as the
     * person's own submit, then the same onboarding policy (the log row carries
     * the advisor as actor). Re-checks that nobody registered the cédula or
     * email while the draft was open.
     */
    @Transactional
    public AffiliationRegistrationDto submitAssisted(UUID advisorUserUuid, UUID uuid) {
        AffiliationRequest request = lockAssistedDraft(advisorUserUuid, uuid);
        requireNotRegistered(request.getDocumentType(), request.getDocumentNumber(), request.getEmail());
        requireNoOpenRequest(request.getDocumentType(), request.getDocumentNumber(), request.getEmail(), request.getId(),
                Status.PENDING);
        return submitDraft(request, actor(advisorUserUuid));
    }

    private Promoter requireAdvisor(UUID advisorUserUuid) {
        return promoterRepository.findActiveByUserUuid(advisorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
    }

    /** An advisor only reaches the assisted drafts attributed to them; anyone else's is a 404, never a 403. */
    private static void requireOwnAssisted(AffiliationRequest request, Promoter advisor) {
        if (!request.isAssisted() || request.getPromoter() == null
                || !request.getPromoter().getId().equals(advisor.getId())) {
            throw new NoSuchElementException("affiliation_request.not_found");
        }
    }

    private AffiliationRequest lockAssistedDraft(UUID advisorUserUuid, UUID uuid) {
        Promoter advisor = requireAdvisor(advisorUserUuid);
        AffiliationRequest request = repository.findForUpdateByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        requireOwnAssisted(request, advisor);
        if (!request.isDraft()) {
            throw new IllegalArgumentException("affiliation_request.draft.invalid_state");
        }
        return request;
    }

    private AffiliationRequest lockOwnDraft(UUID userUuid) {
        AffiliationRequest own = repository.findFirstByUser_UuidOrderByCreatedAtDesc(userUuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        AffiliationRequest request = repository.findForUpdateByUuid(own.getUuid())
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        if (!request.isDraft()) {
            throw new IllegalArgumentException("affiliation_request.draft.invalid_state");
        }
        return request;
    }

    private static void clearReportedPayment(AffiliationRequest r) {
        r.setPaymentMethod(null);
        r.setPaymentBank(null);
        r.setPaymentAmount(null);
        r.setPaymentCurrencyCode(null);
        r.setPaymentIdentification(null);
        r.setPaymentBankAccountType(null);
        r.setPaymentBankAccountCode(null);
        r.setPaymentBankAccountIdentifier(null);
        r.setPaymentPhone(null);
        r.setPaymentEmail(null);
        r.setPaymentReferenceNumber(null);
        r.setPaymentDate(null);
        r.setPaymentSupportFileUrl(null);
        r.setPaymentSupportFileName(null);
        r.setPaymentSupportFileContentType(null);
        r.setPaymentSupportFileSizeBytes(null);
    }

    /** Makes sure the freshly created user's role pivot is visible to the session issuer. */
    private User withRoles(User user, Role role) {
        boolean held = user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getId().equals(role.getId()));
        if (!held) {
            userRoleRepository.findByUserIdAndRoleId(user.getId(), role.getId())
                    .ifPresent(ur -> user.getUserRoles().add(ur));
        }
        return user;
    }

    /** Pending role off, member role on (and default), as {@code AllyRequestService.swapRole} does for allies. */
    private void swapPendingRole(User user, Role memberRole, UUID actorUuid) {
        roleRepository.findByName(RoleNames.MEMBER_PENDING)
                .flatMap(pending -> userRoleRepository.findByUserIdAndRoleId(user.getId(), pending.getId()))
                .ifPresent(current -> {
                    current.setActive(false);
                    userRoleRepository.save(current);
                });
        UserRole target = userRoleRepository.findByUserIdAndRoleId(user.getId(), memberRole.getId()).orElse(null);
        if (target == null) {
            target = new UserRole();
            target.setUser(user);
            target.setRole(memberRole);
        } else {
            target.setActive(true);
            target.setExpiresAt(null);
        }
        target.setAssignedBy(actorUuid);
        userRoleRepository.save(target);
        user.setDefaultRole(memberRole);
    }

    /** Turns the relatives typed in the family step into beneficiaries of the new member. */
    private void addFamily(AffiliationRequest request, UUID memberUuid) {
        if (request.getFamily() == null) return;
        for (Map<String, String> f : request.getFamily()) {
            String birth = f.get("birthDate");
            beneficiariesService.add(memberUuid, new BeneficiaryCreateRequest(
                    f.get("firstName"), null, f.get("lastName"), null,
                    f.get("documentType"), f.get("documentNumber"),
                    birth == null ? null : LocalDate.parse(birth),
                    f.get("phone"), f.get("email"),
                    Beneficiary.Relationship.valueOf(f.get("relationship")), null));
        }
    }

    private AffiliationRegistrationDto toRegistration(AffiliationRequest r) {
        List<AffiliationRegistrationDto.FamilyMember> family = r.getFamily() == null ? List.of()
                : r.getFamily().stream().map(f -> new AffiliationRegistrationDto.FamilyMember(
                        f.get("firstName"), f.get("lastName"), f.get("documentType"), f.get("documentNumber"),
                        f.get("birthDate") == null ? null : LocalDate.parse(f.get("birthDate")),
                        f.get("relationship"), f.get("phone"), f.get("email"))).toList();
        boolean contractAccepted = r.getTermsVersion() != null && r.getTermsAcceptedAt() != null && !termsChanged(r);
        return new AffiliationRegistrationDto(
                r.getUuid(), r.getStatus(), r.getFirstName(), r.getLastName(), r.getDocumentType(),
                r.getDocumentNumber(), r.getBirthDate(), r.getEmail(), r.getPhone(),
                DisplayRefs.ref((Object) r.getPlan()),
                r.getTermsVersion() != null ? r.getTermsVersion().getUuid() : null,
                contractAccepted, paymentOf(r), family,
                r.getPromoter() != null ? r.getPromoter().getDisplayName() : null, r.getReferralCode(),
                r.getSubmittedAt(), r.getRejectionReason(),
                r.isRetainFromCleanup(), r.getRetainNote(), r.getRetainedAt());
    }

    // ─── Rules ──────────────────────────────────────────────────────────────

    /**
     * Rejects plans a single person can't self-enroll in: missing, inactive or
     * unpublished plans (the same visibility rule as {@code GET /v1/public/plans}),
     * and group plans — CORPORATIVO or {@code minMembers > 1}.
     */
    static void requireSelfServicePlan(Plan plan) {
        if (plan == null || !plan.isActive() || !plan.isPublished()) {
            throw new IllegalArgumentException("affiliation.plan.not_available");
        }
        if (plan.getType() == PlanType.CORPORATE
                || (plan.getMinMembers() != null && plan.getMinMembers() > 1)) {
            throw new IllegalArgumentException("affiliation.plan.group_only");
        }
    }

    /** Whether the data as it stands now renders a different contract than the one accepted. */
    boolean termsChanged(AffiliationRequest request) {
        if (request.getTermsVersion() == null || request.getPlan() == null || request.getBirthDate() == null) {
            return false;  // a DRAFT without a contract yet has nothing to compare
        }
        String current = memberContractService.render(request.getTermsVersion(), applicantOf(request));
        return !MemberContractService.sha256(current).equals(request.getTermsContentSha256());
    }

    private void requireNotRegistered(String documentType, String documentNumber, String email) {
        if (personRepository.existsByDocumentTypeAndDocumentNumber(documentType, documentNumber)) {
            throw new IllegalArgumentException("affiliation.document.exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("user.email.exists");
        }
    }

    private AffiliationRequest lockPending(UUID uuid, String invalidStateCode) {
        AffiliationRequest request = repository.findForUpdateByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        if (!request.isPending()) {
            throw new IllegalArgumentException(invalidStateCode);
        }
        return request;
    }

    /** The values the contract is rendered with — the same on submit, on review and on approval. */
    private static MemberTermsApplicant applicantOf(AffiliationRequest request) {
        return new MemberTermsApplicant(request.getFirstName(), request.getLastName(),
                request.getDocumentType(), request.getDocumentNumber(), request.getBirthDate(),
                request.getPlan(), request.getPromoter());
    }

    private static Specification<AffiliationRequest> withStatus(Status status) {
        // Registrations still in progress (DRAFT) stay out of the staff inbox unless asked for.
        return status == null
                ? (root, query, cb) -> cb.notEqual(root.get("status"), Status.DRAFT.name())
                : (root, query, cb) -> cb.equal(root.get("status"), status.name());
    }

    private static Status parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("IN_PROGRESS".equals(normalized)) {
            return Status.DRAFT;
        }
        try {
            return Status.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("affiliation_request.status.invalid");
        }
    }

    // ─── Log ────────────────────────────────────────────────────────────────

    /** {@code plan_Display} / {@code advisor_Display} sort by their label; the secret and the contract text never sort. */
    private static Map<String, SortFieldValidator.SortableField> sortableFields() {
        Map<String, SortFieldValidator.SortableField> fields = new LinkedHashMap<>(
                SortFieldValidator.sortableFieldsOf(AffiliationRequest.class, Map.of(
                        "fullName", "firstName",
                        "plan_Display", "plan.name",
                        "advisor_Display", "promoter.displayName"
                )));
        fields.remove("passwordHash");
        fields.remove("termsRenderedContent");
        fields.remove("paymentSupportFileUrl");
        return Map.copyOf(fields);
    }

    private void writeLog(AffiliationRequest request, Action action, Status from, Status to,
                     List<Map<String, String>> changes, String comment, User actor) {
        AffiliationRequestLog entry = new AffiliationRequestLog();
        entry.setAffiliationRequest(request);
        entry.setAction(action);
        entry.setFromStatus(from != null ? from.name() : null);
        entry.setToStatus(to != null ? to.name() : null);
        entry.setChanges(changes);
        entry.setComment(comment);
        entry.setActor(actor);
        entry.setActionAt(Instant.now());
        logRepository.save(entry);
    }

    private static void track(List<Map<String, String>> changes, String field, String from, String to) {
        if (Objects.equals(from, to)) return;
        Map<String, String> change = new LinkedHashMap<>();
        change.put(AffiliationRequestLog.CHANGE_FIELD, field);
        change.put(AffiliationRequestLog.CHANGE_FROM, from);
        change.put(AffiliationRequestLog.CHANGE_TO, to);
        changes.add(change);
    }

    private User actor(UUID actorUuid) {
        if (actorUuid == null) return null;
        return userRepository.findByUuid(actorUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
    }

    // ─── Mapping ────────────────────────────────────────────────────────────

    private AffiliationRequestListItemDto toListItem(AffiliationRequest r) {
        return new AffiliationRequestListItemDto(
                r.getUuid(), r.getFullName(), r.getDocumentType(), r.getDocumentNumber(), r.getEmail(), r.getPhone(),
                DisplayRefs.ref((Object) r.getPlan()), advisorRef(r.getPromoter()), r.getChannel(),
                r.getStatus(), r.getCreatedAt(), r.getReviewedAt(),
                r.isRetainFromCleanup(), r.getRetainNote(), r.getRetainedAt());
    }

    private AffiliationRequestDetailDto toDetail(AffiliationRequest r) {
        TermsVersion terms = r.getTermsVersion();
        List<AffiliationRequestLogDto> history = logRepository
                .findByAffiliationRequestIdOrderByActionAtDescIdDesc(r.getId()).stream()
                .map(e -> new AffiliationRequestLogDto(e.getAction(), e.getFromStatus(), e.getToStatus(),
                        e.getChanges(), e.getComment(), userRef(e.getActor()), e.getActionAt()))
                .toList();
        return new AffiliationRequestDetailDto(
                r.getUuid(),
                r.getFirstName(), r.getLastName(), r.getFullName(), r.getDocumentType(), r.getDocumentNumber(),
                r.getBirthDate(), r.getEmail(), r.getPhone(), r.getLocale(),
                DisplayRefs.ref((Object) r.getPlan()), advisorRef(r.getPromoter()), r.getReferralCode(),
                r.getExpiresAtOverride(), r.getReinscriptionRequiredOverride(),
                r.getChannel(), createdByRef(r), r.getPasswordHash() == null,
                terms == null ? null : DisplayRef.of(terms.getUuid(), null, terms.getTitle()),
                r.getTermsRenderedContent(), r.getTermsContentSha256(), r.getTermsAcceptedAt(),
                r.getIpAddress(), r.getUserAgent(), termsChanged(r),
                r.getStatus(), userRef(r.getReviewedBy()), r.getReviewedAt(), r.getRejectionReason(),
                userRef(r.getUser()), DisplayRefs.ref(r.getMember()), DisplayRefs.ref(r.getMembership()),
                paymentOf(r), r.getCreatedAt(), history);
    }

    private static AffiliationRequestPaymentDto paymentOf(AffiliationRequest r) {
        if (!r.hasReportedPayment()) return null;
        return new AffiliationRequestPaymentDto(
                r.getPaymentMethod().getName(),
                r.getPaymentBank() != null ? r.getPaymentBank().getName() : null,
                r.getPaymentReferenceNumber(), r.getPaymentAmount(), r.getPaymentCurrencyCode(), r.getPaymentDate(),
                r.getPaymentIdentification(), r.getPaymentPhone(), r.getPaymentEmail(),
                r.getPaymentBankAccountIdentifier(), r.getPaymentSupportFileName(),
                r.getPaymentSupportFileUrl() != null && !r.getPaymentSupportFileUrl().isBlank(),
                r.getPayment() != null ? r.getPayment().getUuid() : null);
    }

    private static DisplayRef advisorRef(Promoter promoter) {
        return promoter == null ? null
                : DisplayRef.of(promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName());
    }

    /** Who submitted an assisted request (the advisor's user); {@code null} for the anonymous public form. */
    private DisplayRef createdByRef(AffiliationRequest r) {
        if (r.getCreatedBy() == null) return null;
        return userRepository.findByUuid(r.getCreatedBy()).map(AffiliationRequestService::userRef).orElse(null);
    }

    /** A user labelled by their person's full name (falls back to the login email). */
    private static DisplayRef userRef(User user) {
        if (user == null) return null;
        String name = user.getPerson() != null ? user.getPerson().getFullName() : null;
        return DisplayRef.of(user.getUuid(), null, name != null && !name.isBlank() ? name : user.getEmail());
    }

    private static AffiliationRequestEvent event(AffiliationRequestEvent.Kind kind, AffiliationRequest r, String reason,
                                                 String activationUrl) {
        return new AffiliationRequestEvent(kind, r.getUuid(), r.getChannel(), r.getEmail(), r.getFullName(), r.getLocale(),
                r.getPlan() != null ? r.getPlan().getName() : null,
                r.getPromoter() != null ? r.getPromoter().getDisplayName() : null,
                reason, activationUrl,
                r.getMembership() != null
                        && Membership.LifecycleStatus.ACTIVE.name().equals(r.getMembership().getStatus()));
    }

    /** TTL of the "create your password" link of an assisted approval ({@code app.affiliation-requests.activation-ttl-hours}). */
    Duration activationTtl() {
        return Duration.ofHours(activationTtlHours > 0 ? activationTtlHours : DEFAULT_ACTIVATION_TTL_HOURS);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String str(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
