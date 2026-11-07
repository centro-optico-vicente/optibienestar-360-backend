package com.fenixcore.optibienestar360.modules.allyrequest.service;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.FileValidationService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.PresignedUrlPolicy;
import com.fenixcore.optibienestar360.common.storage.StorageKeyBuilder;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.core.exception.UnprocessableDetailsException;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyHolderPersonRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AllyAgreementsService;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AgreementSupportUrlDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestApproveRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestDetailDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestInviteRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestListItemDto;
import com.fenixcore.optibienestar360.modules.allyrequest.event.AllyRequestEvent;
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
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProfessionRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.security.RoleNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ally requests — "Solicitar alianza" (hub ADR 0036).
 *
 * <p>The public form creates the <em>real</em> ally in {@code PENDING} plus its
 * login with the chosen password and the {@code ALIADO_PENDIENTE} role, so the
 * applicant enters the portal right away and completes its profile while
 * staff review it. Nothing is public until staff approve it: approving
 * requires an ACTIVE agreement (registered in the same step if missing, with
 * an optional signed support), swaps the role for {@code ALIADO} and may
 * publish the ally. Rejecting suspends the self-registered login; reopening
 * undoes it. Imported allies have no login until staff invite their contact
 * ("create your password" link).</p>
 *
 * <p>Transitions lock the ally row, so a double click or two reviewers can't
 * apply one twice. Emails go out after commit ({@code AllyRequestNotifier}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AllyRequestService {

    static final long DEFAULT_ACTIVATION_TTL_HOURS = 72;
    private static final FileVisibility SUPPORT_VISIBILITY = FileVisibility.CONFIDENTIAL;
    private static final String SUPPORT_OWNER_TABLE = "ally_agreements";
    private static final String AGREEMENT_ACTIVE = "ACTIVE";
    private static final String USER_ACTIVE = "ACTIVE";
    private static final String USER_SUSPENDED = "SUSPENDED";
    private static final Set<String> SORTABLE = Set.of("createdAt", "name", "allyNumber", "registrationStatus",
            "registrationChannel", "approvedAt");

    private final AlliesService alliesService;
    private final AllyAgreementsService agreementsService;
    private final AllyRepository allyRepository;
    private final AllyUserRepository allyUserRepository;
    private final AllyAgreementRepository agreementRepository;
    private final AllyTypeRepository allyTypeRepository;
    private final ProfessionRepository professionRepository;
    private final PromoterRepository promoterRepository;
    private final PersonService personService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final FileValidationService fileValidationService;
    private final ObjectProvider<StorageService> storageProvider;
    private final PresignedUrlPolicy presignedUrlPolicy;

    @Value("${app.ally-requests.activation-ttl-hours:72}")
    private long activationTtlHours = DEFAULT_ACTIVATION_TTL_HOURS;

    // ─── Public form ────────────────────────────────────────────────────────

    @Transactional
    public AllyRequestCreatedDto create(AllyRequestCreateRequest req) {
        if (req.homepage() != null && !req.homepage().isBlank()) {
            // Honeypot filled: a bot. Same generic 422 as any invalid request.
            log.warn("Ally request rejected by honeypot");
            throw new IllegalArgumentException("ally_request.rejected");
        }
        if (!Boolean.TRUE.equals(req.acceptedPortalTerms())) {
            throw new IllegalArgumentException("ally_request.terms.not_accepted");
        }
        Segment segment = req.segment();
        AllyType type = allyTypeRepository.findByUuid(req.allyTypeUuid())
                .filter(AllyType::isActive)
                .orElseThrow(() -> new NoSuchElementException("ally_type.not_found"));
        if (type.getSegment() != segment) {
            throw new IllegalArgumentException("ally_request.type.segment_mismatch");
        }
        Profession profession = null;
        if (segment == Segment.B2B) {
            if (isBlank(req.name())) throw new IllegalArgumentException("ally_request.name.required");
            if (isBlank(req.taxDocumentType()) || isBlank(req.taxDocumentNumber())) {
                throw new IllegalArgumentException("ally_request.tax_document.required");
            }
        } else {
            if (req.professionUuid() == null) throw new IllegalArgumentException("ally_request.profession.required");
            profession = professionRepository.findByUuid(req.professionUuid())
                    .orElseThrow(() -> new NoSuchElementException("profession.not_found"));
        }

        String email = normalizeEmail(req.email());
        String documentNumber = req.documentNumber().trim();
        if (userRepository.existsByEmail(email)) {
            throw signInInstead("ally_request.email.taken");
        }
        Person existing = personService.findByDocument(req.documentType(), documentNumber).orElse(null);
        if (existing != null && userRepository.existsByPersonId(existing.getId())) {
            throw signInInstead("ally_request.document.has_account");
        }
        if (existing != null && segment == Segment.B2P) {
            requireNoDuplicateProfessional(existing, req.cityUuid(), profession);
        }

        String code = isBlank(req.referralCode()) ? null : req.referralCode().trim().toUpperCase(Locale.ROOT);
        Promoter promoter = code == null ? null : promoterRepository.findByReferralCode(code)
                .orElseThrow(() -> new IllegalArgumentException("ally_request.referral.not_found"));

        String firstName = req.firstName().trim();
        String lastName = req.lastName().trim();
        String mobile = req.mobile().trim();
        String allyName = segment == Segment.B2B ? req.name().trim() : (firstName + " " + lastName);
        AllyCreateRequest allyReq = allyCreateRequest(allyName, type.getUuid(), segment,
                segment == Segment.B2B ? req.taxDocumentType() : null,
                segment == Segment.B2B ? req.taxDocumentNumber().trim() : null,
                email, mobile, mobile, null, null, null, req.cityUuid(), null, null,
                new AllyHolderPersonRequest(req.documentType(), documentNumber, firstName, lastName, null, mobile, email),
                promoter != null ? promoter.getUuid() : null,
                profession != null ? List.of(profession.getUuid()) : null);
        AllyDetailDto created = alliesService.createRequest(allyReq, Ally.RegistrationChannel.SELF_SERVICE,
                req.benefitNote());
        Ally ally = allyRepository.findByUuid(created.uuid())
                .orElseThrow(() -> new IllegalStateException("ally just created not found"));

        User user = createPortalUser(email, firstName, lastName, req.documentType(), documentNumber, mobile,
                passwordEncoder.encode(req.password()), RoleNames.ALLY_PENDING);
        linkOwner(ally, user);

        eventPublisher.publishEvent(event(AllyRequestEvent.Kind.RECEIVED, ally, null, null));
        log.info("Ally request {} received ({}, referral {})", ally.getUuid(), segment, code);
        return new AllyRequestCreatedDto(ally.getUuid(), ally.getRegistrationStatus(), email,
                promoter != null ? promoter.getDisplayName() : null);
    }

    /** 422 whose {@code code} property lets the form offer "log in" next to the message. */
    private static UnprocessableDetailsException signInInstead(String code) {
        return new UnprocessableDetailsException(code, null, java.util.Map.of("code", code));
    }

    /** One professional = one ally per site (ADR 0031): the same person, profession and city twice is a duplicate. */
    private void requireNoDuplicateProfessional(Person person, UUID cityUuid, Profession profession) {
        boolean duplicate = allyRepository.findByHolderPersonIdAndSegment(person.getId(), Segment.B2P).stream()
                .filter(a -> a.getRegistrationStatus() != Ally.RegistrationStatus.REJECTED)
                .filter(a -> a.getCity() != null && Objects.equals(a.getCity().getUuid(), cityUuid))
                .anyMatch(a -> profession == null || a.getProfessions().stream()
                        .anyMatch(p -> Objects.equals(p.getId(), profession.getId())));
        if (duplicate) {
            throw new IllegalArgumentException("ally_request.duplicate_professional");
        }
    }

    // ─── Inbox ──────────────────────────────────────────────────────────────

    /** Requests (allies not created from the admin form), newest first by default. */
    public Page<AllyRequestListItemDto> list(Pageable pageable, String status, String channel, String q) {
        Specification<Ally> spec = (root, query, cb) ->
                cb.notEqual(root.get("registrationChannel"), Ally.RegistrationChannel.ADMIN);
        Ally.RegistrationStatus st = parseEnum(Ally.RegistrationStatus.class, status, "ally_request.status.invalid");
        if (st != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("registrationStatus"), st));
        }
        Ally.RegistrationChannel ch = parseEnum(Ally.RegistrationChannel.class, channel, "ally_request.channel.invalid");
        if (ch != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("registrationChannel"), ch));
        }
        if (!isBlank(q)) {
            spec = spec.and(SearchSpecifications.acrossFields(q, "name", "email", "taxDocumentNumber", "tradeName"));
        }
        return allyRepository.findAll(spec, resolveSort(pageable)).map(this::toListItem);
    }

    public List<SortOrder> effectiveSort(Pageable pageable) {
        return resolveSort(pageable).getSort().stream()
                .map(o -> new SortOrder(o.getProperty(), o.getDirection().name()))
                .toList();
    }

    public long count(String status) {
        Ally.RegistrationStatus st = parseEnum(Ally.RegistrationStatus.class, status, "ally_request.status.invalid");
        Specification<Ally> spec = (root, query, cb) ->
                cb.notEqual(root.get("registrationChannel"), Ally.RegistrationChannel.ADMIN);
        if (st != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("registrationStatus"), st));
        }
        return allyRepository.count(spec);
    }

    public AllyRequestDetailDto get(UUID uuid) {
        return toDetail(find(uuid));
    }

    // ─── Transitions ────────────────────────────────────────────────────────

    /**
     * Approves a PENDING request. Needs an ACTIVE agreement — the ally's own,
     * or {@code req.agreement()} registered now; {@code support} (optional) is
     * the signed copy, stored on that agreement. Swaps {@code ALIADO_PENDIENTE}
     * for {@code ALIADO} on the ally's logins and, with {@code publish},
     * publishes the ally.
     */
    @Transactional
    public AllyRequestDetailDto approve(UUID uuid, AllyRequestApproveRequest req, MultipartFile support,
                                        UUID actorUuid) {
        Ally ally = lock(uuid);
        if (ally.getRegistrationStatus() != Ally.RegistrationStatus.PENDING) {
            throw new IllegalArgumentException("ally_request.approve.invalid_state");
        }
        AllyRequestApproveRequest body = req != null ? req : new AllyRequestApproveRequest(null, null, null);
        AllyAgreement supported = null;
        if (body.agreement() != null) {
            AllyRequestApproveRequest.AgreementData a = body.agreement();
            AllyAgreementDto dto = agreementsService.create(ally.getUuid(), new AllyAgreementCreateRequest(
                    a.agreementType(), a.startDate(), a.endDate(), null, null, AGREEMENT_ACTIVE, null,
                    a.autoRenew(), a.noticeDays()));
            supported = agreementRepository.findByUuid(dto.uuid())
                    .orElseThrow(() -> new IllegalStateException("agreement just created not found"));
        }
        List<AllyAgreement> active = activeAgreements(ally);
        if (active.isEmpty()) {
            throw new IllegalArgumentException("ally_request.approve.agreement_required");
        }
        if (support != null && !support.isEmpty()) {
            AllyAgreement target = supported != null ? supported : active.stream()
                    .filter(ag -> isBlank(ag.getSignedPdfUrl()))
                    .findFirst()
                    .orElse(active.getFirst());
            storeSupport(target, support);
        }

        ally.setRegistrationStatus(Ally.RegistrationStatus.APPROVED);
        ally.setApprovedAt(Instant.now());
        ally.setApprovedBy(actorUuid);
        if (Boolean.TRUE.equals(body.publish())) {
            ally.setPublished(true);
            if (ally.getPublishedAt() == null) ally.setPublishedAt(Instant.now());
        }
        if (ally.getJoinedAt() == null) ally.setJoinedAt(LocalDate.now());
        allyRepository.flush();  // the published CHECK sees the approved status

        for (AllyUser member : allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted())) {
            swapRole(member.getUser(), RoleNames.ALLY_PENDING, RoleNames.ALLY, actorUuid);
        }
        eventPublisher.publishEvent(event(AllyRequestEvent.Kind.APPROVED, ally, null, null));
        log.info("Ally request {} approved by {} (published: {})", ally.getUuid(), actorUuid, ally.isPublished());
        return toDetail(ally);
    }

    /** Rejects a PENDING request: the ally leaves the active lists and its self-registered login is suspended. */
    @Transactional
    public AllyRequestDetailDto reject(UUID uuid, String reason, UUID actorUuid) {
        String finalReason = blankToNull(reason);
        if (finalReason == null) {
            throw new IllegalArgumentException("ally_request.reject.reason_required");
        }
        Ally ally = lock(uuid);
        if (ally.getRegistrationStatus() != Ally.RegistrationStatus.PENDING) {
            throw new IllegalArgumentException("ally_request.reject.invalid_state");
        }
        ally.setRegistrationStatus(Ally.RegistrationStatus.REJECTED);
        ally.setRejectedAt(Instant.now());
        ally.setRejectedBy(actorUuid);
        ally.setRejectionReason(finalReason);
        ally.setPublished(false);
        ally.setActive(false);
        for (AllyUser member : allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted())) {
            User user = member.getUser();
            if (onlyPendingRole(user)) {
                user.setStatus(USER_SUSPENDED);
            }
        }
        eventPublisher.publishEvent(event(AllyRequestEvent.Kind.REJECTED, ally, finalReason, null));
        log.info("Ally request {} rejected by {}", ally.getUuid(), actorUuid);
        return toDetail(ally);
    }

    /** Undoes a rejection: back to PENDING, active, and the suspended pending logins work again. */
    @Transactional
    public AllyRequestDetailDto reopen(UUID uuid, UUID actorUuid) {
        Ally ally = lock(uuid);
        if (ally.getRegistrationStatus() != Ally.RegistrationStatus.REJECTED) {
            throw new IllegalArgumentException("ally_request.reopen.invalid_state");
        }
        ally.setRegistrationStatus(Ally.RegistrationStatus.PENDING);
        ally.setRejectedAt(null);
        ally.setRejectedBy(null);
        ally.setRejectionReason(null);
        ally.setActive(true);
        for (AllyUser member : allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted())) {
            User user = member.getUser();
            if (USER_SUSPENDED.equals(user.getStatus()) && onlyPendingRole(user)) {
                user.setStatus(USER_ACTIVE);
            }
        }
        log.info("Ally request {} reopened by {}", ally.getUuid(), actorUuid);
        return toDetail(ally);
    }

    /**
     * Invites the contact of an ally without a login (typically imported):
     * creates the user with an unusable password and the role that matches
     * the request status, links it as OWNER and emails the "create your
     * password" link.
     */
    @Transactional
    public AllyRequestDetailDto invite(UUID uuid, AllyRequestInviteRequest req, UUID actorUuid) {
        Ally ally = lock(uuid);
        if (ally.getRegistrationStatus() == Ally.RegistrationStatus.REJECTED) {
            throw new IllegalArgumentException("ally_request.invite.invalid_state");
        }
        if (!allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted()).isEmpty()) {
            throw new IllegalArgumentException("ally_request.invite.already_has_user");
        }
        AllyRequestInviteRequest body = req != null ? req : new AllyRequestInviteRequest(null, null, null, null, null);
        Person holder = ally.getHolderPerson();
        if (holder == null) {
            if (isBlank(body.documentType()) || isBlank(body.documentNumber())) {
                throw new IllegalArgumentException("ally_request.invite.document_required");
            }
            String[] names = splitName(ally.getRepresentativeName());
            String first = !isBlank(body.firstName()) ? body.firstName().trim() : names[0];
            String last = !isBlank(body.lastName()) ? body.lastName().trim() : names[1];
            if (isBlank(first) || isBlank(last)) {
                throw new IllegalArgumentException("ally_request.invite.name_required");
            }
            Person seed = new Person();
            seed.setDocumentType(body.documentType());
            seed.setDocumentNumber(body.documentNumber().trim());
            seed.setFirstName(first);
            seed.setLastName(last);
            seed.setPhone(blankToNull(ally.getWhatsapp() != null ? ally.getWhatsapp() : ally.getPhone()));
            seed.setEmail(blankToNull(ally.getEmail()));
            holder = personService.findOrCreate(seed);
            ally.setHolderPerson(holder);
        }
        if (userRepository.existsByPersonId(holder.getId())) {
            throw new IllegalArgumentException("ally_request.document.has_account");
        }
        String email = normalizeEmail(firstNonBlank(body.email(), holder.getEmail(), ally.getEmail()));
        if (email == null) {
            throw new IllegalArgumentException("ally_request.invite.email_required");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("ally_request.email.taken");
        }
        // 64 hex chars: BCrypt rejects inputs over 72 bytes. Unusable until the link is used.
        String unusable = passwordEncoder.encode(UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        User user = createPortalUser(email, holder.getFirstName(), holder.getLastName(), holder.getDocumentType(),
                holder.getDocumentNumber(), holder.getPhone(), unusable,
                ally.isApproved() ? RoleNames.ALLY : RoleNames.ALLY_PENDING);
        linkOwner(ally, user);
        String activationUrl = authService.issuePasswordSetupLink(user, activationTtl(), Locale.forLanguageTag("es"));
        eventPublisher.publishEvent(new AllyRequestEvent(AllyRequestEvent.Kind.INVITED, ally.getUuid(), ally.getName(),
                holder.getFirstName() + " " + holder.getLastName(), email, ally.getSegment(),
                ally.getRegistrationChannel(), promoterName(ally), null, null, activationUrl, "es"));
        log.info("Ally request {}: contact invited by {}", ally.getUuid(), actorUuid);
        return toDetail(ally);
    }

    /** Short-lived link to the signed support of one of the ally's agreements. */
    public AgreementSupportUrlDto supportUrl(UUID allyUuid, UUID agreementUuid) {
        Ally ally = find(allyUuid);
        AllyAgreement agreement = agreementRepository.findByUuid(agreementUuid)
                .filter(a -> a.getAlly().getId().equals(ally.getId()))
                .orElseThrow(() -> new NoSuchElementException("ally_agreement.not_found"));
        String key = agreement.getSignedPdfUrl();
        if (isBlank(key)) {
            throw new NoSuchElementException("ally_agreement.support.not_available");
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return new AgreementSupportUrlDto(key, null);  // legacy external link
        }
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalArgumentException("payment.support.storage_unavailable");
        }
        Duration ttl = presignedUrlPolicy.clamp(null);
        return new AgreementSupportUrlDto(storage.generatePresignedUrl(key, ttl), Instant.now().plus(ttl));
    }

    // ─── Shared with the importer ───────────────────────────────────────────

    /**
     * {@link AllyCreateRequest} has ~40 positional components; this keeps the
     * request builders readable. Fields not listed stay {@code null}.
     */
    public static AllyCreateRequest allyCreateRequest(String name, UUID allyTypeUuid, Segment segment,
                                                      String taxDocumentType, String taxDocumentNumber,
                                                      String email, String phone, String whatsapp,
                                                      String website, String instagram, String facebook,
                                                      UUID cityUuid, String address, String description,
                                                      AllyHolderPersonRequest holder, UUID promoterUuid,
                                                      List<UUID> professionUuids) {
        return new AllyCreateRequest(name, List.of(allyTypeUuid), taxDocumentType, taxDocumentNumber,
                email, phone, website, whatsapp, instagram, facebook, address, cityUuid, null, null,
                null, description, null, false, null,
                segment, holder,
                null, null, null, null, null,
                null, null, null, null, null, null, null,
                promoterUuid, null, null, null,
                professionUuids);
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    private User createPortalUser(String email, String firstName, String lastName, String documentType,
                                  String documentNumber, String phone, String passwordHash, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("role " + roleName + " missing"));
        UserDto dto = userService.createUserWithPasswordHash(new AdminCreateUserRequest(
                email, firstName, null, lastName, null, null, documentType, documentNumber, null, null,
                blankToNull(phone), List.of(role.getUuid()), role.getUuid()), passwordHash, null);
        return userRepository.findByUuid(dto.uuid())
                .orElseThrow(() -> new IllegalStateException("user just created not found"));
    }

    private void linkOwner(Ally ally, User user) {
        AllyUser link = new AllyUser();
        link.setAlly(ally);
        link.setUser(user);
        link.setAllyRole(AllyRole.OWNER);
        link.setPrimary(true);
        link.setJoinedAt(LocalDate.now());
        allyUserRepository.save(link);
    }

    /** Deactivates {@code from} (if held) and makes {@code to} an effective, default role. */
    private void swapRole(User user, String from, String to, UUID actorUuid) {
        Role fromRole = roleRepository.findByName(from).orElse(null);
        Role toRole = roleRepository.findByName(to)
                .orElseThrow(() -> new IllegalStateException("role " + to + " missing"));
        boolean held = false;
        if (fromRole != null) {
            UserRole current = userRoleRepository.findByUserIdAndRoleId(user.getId(), fromRole.getId()).orElse(null);
            if (current != null && current.isActive()) {
                current.setActive(false);
                userRoleRepository.save(current);
                held = true;
            }
        }
        if (!held) {
            return;  // a staff login linked by hand keeps its roles
        }
        UserRole target = userRoleRepository.findByUserIdAndRoleId(user.getId(), toRole.getId()).orElse(null);
        if (target == null) {
            target = new UserRole();
            target.setUser(user);
            target.setRole(toRole);
            target.setAssignedBy(actorUuid);
        } else {
            target.setActive(true);
            target.setExpiresAt(null);
            target.setAssignedBy(actorUuid);
        }
        userRoleRepository.save(target);
        user.setDefaultRole(toRole);
        if (USER_SUSPENDED.equals(user.getStatus())) {
            user.setStatus(USER_ACTIVE);
        }
    }

    /** The login only carries the pending role — i.e. it exists because of this request. */
    private boolean onlyPendingRole(User user) {
        List<UserRole> roles = userRoleRepository.findByUserIdAndActiveTrue(user.getId());
        return !roles.isEmpty() && roles.stream()
                .allMatch(ur -> RoleNames.ALLY_PENDING.equals(ur.getRole().getName()));
    }

    private void storeSupport(AllyAgreement agreement, MultipartFile file) {
        fileValidationService.validate(SUPPORT_VISIBILITY, file);
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.info("R2 storage disabled — signed agreement support of {} discarded", agreement.getUuid());
            return;
        }
        String key = StorageKeyBuilder.build(SUPPORT_VISIBILITY, SUPPORT_OWNER_TABLE, agreement.getUuid(),
                file.getOriginalFilename());
        try {
            storage.upload(key, file.getInputStream(), file.getSize(),
                    FileValidationService.safeContentType(file.getOriginalFilename()));
            agreement.setSignedPdfUrl(key);
        } catch (IOException ex) {
            log.error("Failed to read the signed agreement support", ex);
            throw new IllegalArgumentException("ally_agreement.support.upload_failed");
        }
    }

    private List<AllyAgreement> activeAgreements(Ally ally) {
        return agreementRepository.findByAllyIdAndStatusAndActiveTrue(ally.getId(), AGREEMENT_ACTIVE);
    }

    private Ally find(UUID uuid) {
        return allyRepository.findByUuid(uuid)
                .filter(a -> a.getRegistrationChannel() != Ally.RegistrationChannel.ADMIN)
                .orElseThrow(() -> new NoSuchElementException("ally_request.not_found"));
    }

    private Ally lock(UUID uuid) {
        return allyRepository.findForUpdateByUuid(uuid)
                .filter(a -> a.getRegistrationChannel() != Ally.RegistrationChannel.ADMIN)
                .orElseThrow(() -> new NoSuchElementException("ally_request.not_found"));
    }

    private Pageable resolveSort(Pageable pageable) {
        Sort requested = pageable.getSort().stream()
                .filter(o -> SORTABLE.contains(o.getProperty()))
                .map(o -> new Sort.Order(o.getDirection(), o.getProperty()))
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), Sort::by));
        Sort sort = requested.isSorted() ? requested : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private AllyRequestEvent event(AllyRequestEvent.Kind kind, Ally ally, String reason, String activationUrl) {
        Person holder = ally.getHolderPerson();
        String locale = LocaleContextHolder.getLocale().getLanguage().equals("en") ? "en" : "es";
        return new AllyRequestEvent(kind, ally.getUuid(), ally.getName(), contactName(ally),
                contactEmail(ally), ally.getSegment(), ally.getRegistrationChannel(), promoterName(ally),
                ally.getRegistrationNote(), reason, activationUrl,
                kind == AllyRequestEvent.Kind.RECEIVED ? locale : "es");
    }

    // ─── Mapping ────────────────────────────────────────────────────────────

    private AllyRequestListItemDto toListItem(Ally ally) {
        boolean hasUser = !allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted()).isEmpty();
        return new AllyRequestListItemDto(ally.getUuid(), ally.getAllyNumber(), ally.getName(), ally.getSegment(),
                typeNames(ally), ally.getRegistrationStatus(), ally.getRegistrationChannel(),
                contactName(ally), contactEmail(ally), contactPhone(ally), DisplayRefs.ref(ally.getCity()),
                ally.getRegistrationNote(), ally.getCreatedAt(), hasUser, !activeAgreements(ally).isEmpty());
    }

    private AllyRequestDetailDto toDetail(Ally ally) {
        Person p = ally.getHolderPerson();
        AllyRequestDetailDto.Holder holder = p == null ? null : new AllyRequestDetailDto.Holder(
                p.getFirstName(), p.getLastName(), p.getDocumentType(), p.getDocumentNumber(), p.getEmail(), p.getPhone());
        List<AllyRequestDetailDto.Agreement> agreements = activeAgreements(ally).stream()
                .map(a -> new AllyRequestDetailDto.Agreement(a.getUuid(), a.getAgreementType(), a.getStartDate(),
                        a.getEndDate(), !isBlank(a.getSignedPdfUrl())))
                .toList();
        List<AllyRequestDetailDto.PortalUser> users = allyUserRepository
                .findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted()).stream()
                .map(AllyUser::getUser)
                .map(u -> new AllyRequestDetailDto.PortalUser(u.getUuid(), u.getEmail(), u.getStatus(),
                        userRoleRepository.findByUserIdAndActiveTrue(u.getId()).stream()
                                .map(ur -> ur.getRole().getName()).sorted().toList()))
                .toList();
        List<String> professions = ally.getProfessions().stream().map(Profession::getName).sorted().toList();
        return new AllyRequestDetailDto(ally.getUuid(), ally.getAllyNumber(), ally.getName(), ally.getSegment(),
                typeNames(ally), professions, ally.getRegistrationStatus(), ally.getRegistrationChannel(),
                ally.getTaxDocumentType(), ally.getTaxDocumentNumber(),
                contactName(ally), contactEmail(ally), contactPhone(ally), holder, ally.getAddress(),
                DisplayRefs.ref(ally.getCity()), DisplayRefs.ref(ally.getCapturedByPromoter()),
                ally.getRegistrationNote(), ally.isPublished(), ally.getCreatedAt(),
                ally.getApprovedAt(), userRef(ally.getApprovedBy()),
                ally.getRejectedAt(), userRef(ally.getRejectedBy()), ally.getRejectionReason(),
                !users.isEmpty(), !agreements.isEmpty(), agreements, users);
    }

    private com.fenixcore.optibienestar360.core.display.DisplayRef userRef(UUID userUuid) {
        return userUuid == null ? null : userRepository.findByUuid(userUuid).map(DisplayRefs::ref).orElse(null);
    }

    private static List<String> typeNames(Ally ally) {
        return ally.getAllyTypes().stream().map(AllyType::getName).sorted(Comparator.naturalOrder()).toList();
    }

    private static String contactName(Ally ally) {
        Person p = ally.getHolderPerson();
        if (p != null) return (p.getFirstName() + " " + p.getLastName()).trim();
        return ally.getRepresentativeName();
    }

    private static String contactEmail(Ally ally) {
        Person p = ally.getHolderPerson();
        return firstNonBlank(p != null ? p.getEmail() : null, ally.getEmail());
    }

    private static String contactPhone(Ally ally) {
        Person p = ally.getHolderPerson();
        return firstNonBlank(p != null ? p.getPhone() : null, ally.getWhatsapp(), ally.getPhone());
    }

    private static String promoterName(Ally ally) {
        return ally.getCapturedByPromoter() != null ? ally.getCapturedByPromoter().getDisplayName() : null;
    }

    Duration activationTtl() {
        return Duration.ofHours(activationTtlHours > 0 ? activationTtlHours : DEFAULT_ACTIVATION_TTL_HOURS);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String errorCode) {
        if (isBlank(value)) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorCode);
        }
    }

    static String[] splitName(String full) {
        if (isBlank(full)) return new String[]{null, null};
        String t = full.trim();
        int space = t.indexOf(' ');
        return space < 0 ? new String[]{t, null} : new String[]{t.substring(0, space), t.substring(space + 1).trim()};
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }

    private static String normalizeEmail(String email) {
        return isBlank(email) ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
