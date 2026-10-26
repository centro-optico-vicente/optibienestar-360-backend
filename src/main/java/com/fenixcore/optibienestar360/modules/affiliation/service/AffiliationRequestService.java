package com.fenixcore.optibienestar360.modules.affiliation.service;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestDetailDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListItemDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestLogDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestUpdateRequest;
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
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipsService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** Hours the "create your password" link of an assisted approval stays valid. */
    @Value("${app.affiliation-requests.activation-ttl-hours:72}")
    private long activationTtlHours = DEFAULT_ACTIVATION_TTL_HOURS;

    // ─── Public form ────────────────────────────────────────────────────────

    @Transactional
    public AffiliationRequestCreatedDto create(AffiliationRequestCreateRequest req, ClientRequestMetadata client) {
        String code = req.referralCode() == null || req.referralCode().isBlank()
                ? null : req.referralCode().trim().toUpperCase(Locale.ROOT);
        Applicant applicant = new Applicant(req.firstName(), req.lastName(), req.documentType(), req.documentNumber(),
                req.birthDate(), req.email(), req.phone(), req.planUuid(), req.termsVersionUuid());
        validateApplicant(applicant);
        Promoter promoter = code == null ? null : promoterRepository.findByReferralCode(code)
                .orElseThrow(() -> new IllegalArgumentException("affiliation.referral.not_found"));
        return store(applicant, Channel.SELF_SERVICE, promoter, code, passwordEncoder.encode(req.password()),
                client, null);
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
                actor(advisorUserUuid));
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
                                               String passwordHash, ClientRequestMetadata client, User actor) {
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
        repository.save(request);

        writeLog(request, Action.CREATED, null, Status.PENDING, null, null, actor);
        eventPublisher.publishEvent(event(AffiliationRequestEvent.Kind.RECEIVED, request, null, null));
        log.info("Affiliation request {} received ({}, referral {})", request.getUuid(), channel, code);
        return new AffiliationRequestCreatedDto(request.getUuid(), request.getStatus(), email,
                promoter != null ? promoter.getDisplayName() : null);
    }

    // ─── Reads ──────────────────────────────────────────────────────────────

    /** Admin list, optional {@code status} filter and free-text {@code q}, newest first by default. */
    public Page<AffiliationRequestListItemDto> list(Pageable pageable, String status, String q) {
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);
        Specification<AffiliationRequest> spec = withStatus(parseStatus(status));
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolved).map(this::toListItem);
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
        Promoter advisor = promoterRepository.findActiveByUserUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);
        Long advisorId = advisor.getId();
        Specification<AffiliationRequest> spec = withStatus(parseStatus(status))
                .and((root, query, cb) -> cb.equal(root.get("promoter").get("id"), advisorId));
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
        // Someone may have registered the same cédula/email since the request arrived.
        requireNotRegistered(request.getDocumentType(), request.getDocumentNumber(), request.getEmail());
        requireSelfServicePlan(request.getPlan());

        Role memberRole = roleRepository.findByName(RoleNames.MEMBER)
                .orElseThrow(() -> new IllegalStateException("role " + RoleNames.MEMBER + " missing"));

        // Assisted request without password: an unusable random one until the
        // member sets theirs through the activation link in the approval email.
        boolean passwordPending = request.getPasswordHash() == null;
        String passwordHash = passwordPending
                ? passwordEncoder.encode(UUID.randomUUID() + ":" + UUID.randomUUID())
                : request.getPasswordHash();
        UserDto createdUser = userService.createUserWithPasswordHash(new AdminCreateUserRequest(
                request.getEmail(), request.getFirstName(), null, request.getLastName(), null, null,
                request.getDocumentType(), request.getDocumentNumber(), null, null, request.getPhone(),
                List.of(memberRole.getUuid()), memberRole.getUuid()), passwordHash, actorUuid);

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
                new MembershipCreateRequest(request.getPlan().getUuid(), null, null, null, null), actorUuid);
        Membership membership = membershipRepository.findByUuid(enrolled.uuid())
                .orElseThrow(() -> new IllegalStateException("membership just created not found"));
        membership.setStatus(Membership.LifecycleStatus.SUSPENDED.name());

        User user = userRepository.findByUuid(createdUser.uuid())
                .orElseThrow(() -> new IllegalStateException("user just created not found"));
        Member member = memberRepository.findByUuid(memberDto.uuid())
                .orElseThrow(() -> new IllegalStateException("member just created not found"));

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
                request.getPlan());
    }

    private static Specification<AffiliationRequest> withStatus(Status status) {
        return status == null
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("status"), status.name());
    }

    private static Status parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
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
                r.getStatus(), r.getCreatedAt(), r.getReviewedAt());
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
                r.getChannel(), createdByRef(r), r.getPasswordHash() == null,
                terms == null ? null : DisplayRef.of(terms.getUuid(), null, terms.getTitle()),
                r.getTermsRenderedContent(), r.getTermsContentSha256(), r.getTermsAcceptedAt(),
                r.getIpAddress(), r.getUserAgent(), termsChanged(r),
                r.getStatus(), userRef(r.getReviewedBy()), r.getReviewedAt(), r.getRejectionReason(),
                userRef(r.getUser()), DisplayRefs.ref(r.getMember()), DisplayRefs.ref(r.getMembership()),
                r.getCreatedAt(), history);
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
                reason, activationUrl);
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
