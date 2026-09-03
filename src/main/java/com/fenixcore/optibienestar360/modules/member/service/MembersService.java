package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.Gender;
import com.fenixcore.optibienestar360.modules.catalog.entity.MaritalStatus;
import com.fenixcore.optibienestar360.modules.catalog.entity.Occupation;
import com.fenixcore.optibienestar360.modules.catalog.repository.CityRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.GenderRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.MaritalStatusRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.OccupationRepository;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberListItemDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MedicalRecordRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberDocumentRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberPromoterAssignmentRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationEnqueueCommand;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationService;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterMemberContactRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.ReferralRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterResolver;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for {@link Member} — admin CRUD + RSQL list. Person
 * fields delegate to {@link PersonService#findOrCreate} so the same human
 * appearing as a User AND a Member shares one persons row instead of
 * duplicating.
 *
 * <p>Beneficiary / document / medical-record CRUD lives in sub-resource
 * services (own bullets); this one only exposes the count helpers needed by
 * {@link MemberDetailDto}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembersService {

    /**
     * RSQL whitelist — restricts the {@code ?filter=} param to a known set
     * of safe leaf names. Anything else is rejected with 422 before hitting
     * the JPA criteria builder, defending against attempts at traversing
     * the entity graph (e.g. {@code person.passwordHash=='...'}).
     */
    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "enrolledAt", "notes",
            "person.firstName", "person.lastName", "person.documentType",
            "person.documentNumber", "person.email", "person.phone",
            "createdAt", "updatedAt", "active", "status"
    );

    /**
     * Free-text {@code ?q=} fields. The composed {@code person.fullName} is
     * a Postgres GENERATED column with a GIN unaccent index from V15, so
     * the search is accent-insensitive ({@code "merida"} matches
     * {@code "Mérida"}) with no extra cost.
     */
    private static final String[] SEARCHABLE_FIELDS = {
            "person.fullName", "person.documentNumber", "person.email", "person.phone"
    };

    /**
     * {@code city_Display} and {@code currentPromoter_Display} (ADR 0014)
     * aren't {@code Member}'s own fields — {@code city} lives on
     * {@code person} (2-hop path) and {@code currentPromoter} is the
     * DTO name for the entity's {@code promoter} relation (see
     * {@code MemberMapper.toListItem}).
     */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Member.class, Map.of(
                    "fullName", "person.fullName",
                    "documentType", "person.documentType",
                    "documentNumber", "person.documentNumber",
                    "phone", "person.phone",
                    "city_Display", "person.city.name",
                    "currentPromoter_Display", "promoter.displayName"
            ));

    private final MemberRepository memberRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final MemberDocumentRepository documentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PersonService personService;
    private final OccupationRepository occupationRepository;
    private final GenderRepository genderRepository;
    private final MaritalStatusRepository maritalStatusRepository;
    private final CityRepository cityRepository;
    private final PromoterResolver promoterResolver;
    private final NotificationService notificationService;
    private final MessageSource messageSource;
    private final MemberMapper mapper;
    private final MembershipRepository membershipRepository;
    private final CommissionRepository commissionRepository;
    private final SubsidyRepository subsidyRepository;
    private final ReferralRepository referralRepository;
    private final MemberPromoterAssignmentRepository memberPromoterAssignmentRepository;
    private final PromoterMemberContactRepository promoterMemberContactRepository;
    private final DefaultSortResolver defaultSortResolver;

    // ─── Read ───────────────────────────────────────────────────────────────

    public MemberDetailDto getDetail(UUID uuid) {
        return toDetailWithCounts(findManaged(uuid));
    }

    /**
     * Self-service "my member record" lookup. Resolves the member of the
     * JWT-authenticated user via the {@code user.person → member.person}
     * link and returns the same {@link MemberDetailDto} the admin endpoint
     * exposes — staff users (with no member row) and members whose record
     * was soft-deleted both get a 404, so the front-end treats them the
     * same way ("you are not enrolled").
     */
    public MemberDetailDto getMyMember(UUID actorUserUuid) {
        Member member = memberRepository.findByUserUuid(actorUserUuid)
                .filter(Member::isActive)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));
        return toDetailWithCounts(member);
    }

    public Page<MemberListItemDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "member", pageable, new SortOrder("enrolledAt", "DESC"));
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "member");
        Specification<Member> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "member.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return memberRepository.findAll(spec, resolvedPageable).map(mapper::toListItem);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("member", pageable, new SortOrder("enrolledAt", "DESC"));
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Member> spec = activeOnly().and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(memberRepository, memberRepository::findByUuid, spec, currentValues, limit,
                Member::getUuid, member -> member.getPerson().getDocumentNumber(),
                member -> member.getPerson().getFullName(), Member::isActive);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "member", action = AuditAction.CREATE)
    public MemberDetailDto create(MemberCreateRequest req) {
        Person person = resolvePerson(req);

        // Member-side dedupe — UNIQUE(person_id) on V17 would catch it
        // anyway, but pre-checking lets us return a clean 422 instead of
        // the misleading 409 from the unique violation.
        if (memberRepository.existsByPersonId(person.getId())) {
            throw new IllegalArgumentException("member.person.already_enrolled");
        }

        Member saved = memberRepository.save(buildMember(req, person));
        enqueueWelcome(saved);
        return toDetailWithCounts(saved);
    }

    /**
     * Enqueues the welcome notification (vertical-9) onto the persistent queue.
     * Idempotent by (member uuid, template) so it fires at most once per member.
     * A missing email is a silent skip — enrollment succeeds regardless.
     */
    private void enqueueWelcome(Member member) {
        Person person = member.getPerson();
        if (person == null) return;
        String email = person.getEmail();
        if (email == null || email.isBlank()) return;

        Locale locale = localeOf(person.getLocale());
        String subject = messageSource.getMessage("email.welcome.subject", null, locale);
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", Optional.ofNullable(person.getFullName()).orElse(""));

        notificationService.enqueue(new NotificationEnqueueCommand(
                email, null, locale.getLanguage(), "welcome", subject, vars,
                "member", member.getUuid(), null, true));
    }

    private static Locale localeOf(String tag) {
        if (tag == null || tag.isBlank()) return Locale.forLanguageTag("es");
        try {
            return Locale.forLanguageTag(tag);
        } catch (RuntimeException ex) {
            return Locale.forLanguageTag("es");
        }
    }

    /**
     * Enrolls one person under a corporate contract (V38 bulk flow). Shares the
     * person-resolution and member-building logic with {@link #create} but
     * differs in the duplicate case: instead of throwing (which would poison
     * the surrounding bulk transaction), an already-enrolled person yields
     * {@code null} so the caller can record a skip and keep going. The FK to
     * the contract is stamped here; the caller keeps {@code actual_member_count}
     * in sync.
     *
     * @return the persisted member, or {@code null} when the person is already
     *         enrolled and was skipped
     */
    @Transactional
    public Member enrollForCorporate(MemberCreateRequest req, CorporateContract contract) {
        Person person = resolvePerson(req);
        if (memberRepository.existsByPersonId(person.getId())) {
            return null;   // skip duplicate — never throw inside the batch tx
        }
        Member member = buildMember(req, person);
        member.setCorporateContract(contract);
        return memberRepository.save(member);
    }

    /**
     * Builds (or reuses) the {@link Person} identity-hub row for an enrollment
     * request. {@code findOrCreate} dedupes by (document_type, document_number):
     * when the person already exists (e.g. they were a User or a Beneficiary of
     * another titular first), the seed payload is ignored and the existing
     * managed row is returned — admin must update demographics explicitly via
     * PUT.
     */
    private Person resolvePerson(MemberCreateRequest req) {
        Person personSeed = new Person();
        personSeed.setFirstName(req.firstName());
        personSeed.setMiddleName(req.middleName());
        personSeed.setLastName(req.lastName());
        personSeed.setSecondLastName(req.secondLastName());
        personSeed.setDocumentType(req.documentType());
        personSeed.setDocumentNumber(req.documentNumber());
        personSeed.setTaxDocumentType(req.taxDocumentType());
        personSeed.setTaxDocumentNumber(req.taxDocumentNumber());
        personSeed.setBirthDate(req.birthDate());
        personSeed.setGender(resolveGender(req.genderUuid()));
        personSeed.setMaritalStatus(resolveMaritalStatus(req.maritalStatusUuid()));
        personSeed.setBirthplace(req.birthplace());
        personSeed.setNumberOfChildren(req.numberOfChildren());
        personSeed.setSpouseName(req.spouseName());
        personSeed.setPhone(req.phone());
        personSeed.setLandlinePhone(req.landlinePhone());
        personSeed.setEmail(req.email());
        personSeed.setLocale(req.locale());
        personSeed.setAddress(req.address());
        personSeed.setCity(resolveCity(req.cityUuid()));
        return personService.findOrCreate(personSeed);
    }

    /**
     * Assembles a new {@link Member} from the request and its resolved person,
     * including the permanent promoter attribution (v2 PDF #4/#5): the referral
     * code resolves to a promoter (promoter-table precedence) or falls back to
     * the INSTITUCION system promoter. Does not persist — the caller decides.
     */
    private Member buildMember(MemberCreateRequest req, Person person) {
        Member member = new Member();
        member.setPerson(person);
        member.setOccupation(resolveOccupation(req.occupationUuid()));
        member.setEmployerName(req.employerName());
        member.setJobPosition(req.jobPosition());
        member.setEmployerAddress(req.employerAddress());
        if (req.enrolledAt() != null) {
            member.setEnrolledAt(req.enrolledAt());
        }
        member.setNotes(req.notes());
        member.setPromoter(promoterResolver.resolveForEnrollment(req.referralCode()));
        return member;
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "member", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public MemberDetailDto update(UUID uuid, MemberUpdateRequest req) {
        Member member = findManaged(uuid);
        Person person = member.getPerson();

        // Person-side updates (delegated to the linked persons row).
        if (req.firstName()         != null) person.setFirstName(req.firstName());
        if (req.middleName()        != null) person.setMiddleName(req.middleName());
        if (req.lastName()          != null) person.setLastName(req.lastName());
        if (req.secondLastName()    != null) person.setSecondLastName(req.secondLastName());
        if (req.documentType()      != null) person.setDocumentType(req.documentType());
        if (req.documentNumber()    != null) person.setDocumentNumber(req.documentNumber());
        if (req.taxDocumentType()   != null) person.setTaxDocumentType(req.taxDocumentType());
        if (req.taxDocumentNumber() != null) person.setTaxDocumentNumber(req.taxDocumentNumber());
        if (req.birthDate()         != null) person.setBirthDate(req.birthDate());
        if (req.genderUuid()        != null) person.setGender(resolveGender(req.genderUuid()));
        if (req.maritalStatusUuid() != null) person.setMaritalStatus(resolveMaritalStatus(req.maritalStatusUuid()));
        if (req.birthplace()        != null) person.setBirthplace(req.birthplace());
        if (req.numberOfChildren()  != null) person.setNumberOfChildren(req.numberOfChildren());
        if (req.spouseName()        != null) person.setSpouseName(req.spouseName());
        if (req.phone()             != null) person.setPhone(req.phone());
        if (req.landlinePhone()     != null) person.setLandlinePhone(req.landlinePhone());
        if (req.email()             != null) person.setEmail(req.email());
        if (req.locale()            != null) person.setLocale(req.locale());
        if (req.address()           != null) person.setAddress(req.address());
        if (req.cityUuid()          != null) person.setCity(resolveCity(req.cityUuid()));

        // Member-side updates.
        if (req.occupationUuid()  != null) member.setOccupation(resolveOccupation(req.occupationUuid()));
        if (req.employerName()    != null) member.setEmployerName(req.employerName());
        if (req.jobPosition()     != null) member.setJobPosition(req.jobPosition());
        if (req.employerAddress() != null) member.setEmployerAddress(req.employerAddress());
        if (req.enrolledAt()     != null) member.setEnrolledAt(req.enrolledAt());
        if (req.notes()          != null) member.setNotes(req.notes());
        if (req.active()         != null) member.setActive(req.active());
        if (req.status()         != null) member.setStatus(req.status());

        return toDetailWithCounts(member);
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    /**
     * Counts real, independent (non-cascade-owned) FK references to this
     * member. {@code Member.beneficiaries} and {@code Member.documents} are
     * declared as plain {@code @OneToMany(mappedBy = "member")} with NO
     * {@code cascade}/{@code orphanRemoval} — Hibernate will NOT cascade a
     * hard delete to them, so (like {@code Ally}'s children) they count as
     * genuine blockers rather than owned children to exclude. On top of
     * those, this also counts every other table with a real FK to
     * {@code members}: {@code memberships}, {@code commissions},
     * {@code subsidies}, {@code referrals} (both as referrer and as
     * referred), {@code member_promoter_assignments}, and
     * {@code promoter_member_contacts} (the last two are insert-only audit
     * logs, but their rows are still real FK rows that would violate a hard
     * delete). {@code MedicalRecord} is intentionally excluded — it FKs to
     * {@code Person}, not {@code Member} (shared identity hub, not a
     * member-owned relationship).
     */
    public long countUsages(UUID uuid) {
        Member member = findManaged(uuid);
        Long id = member.getId();
        long beneficiaries = beneficiaryRepository.countByMemberId(id);
        long documents = documentRepository.countByMemberId(id);
        long memberships = membershipRepository.countByMemberId(id);
        long commissions = commissionRepository.countByMemberId(id);
        long subsidies = subsidyRepository.countByMemberId(id);
        long referralsAsReferrer = referralRepository.countByReferrerId(id);
        long referralsAsReferred = referralRepository.countByReferredId(id);
        long promoterAssignments = memberPromoterAssignmentRepository.countByMemberId(id);
        long promoterContacts = promoterMemberContactRepository.countByMemberId(id);
        return beneficiaries + documents + memberships + commissions + subsidies
                + referralsAsReferrer + referralsAsReferred + promoterAssignments + promoterContacts;
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /**
     * Smart delete: hard-deletes only when {@code physical=true} AND the
     * member is genuinely unreferenced (re-checked here, not trusted from the
     * caller, to avoid a race between the usage check and the delete).
     * Otherwise falls back to the existing soft-delete. Omitting
     * {@code physical} (default {@code false}) reproduces the prior
     * behavior exactly.
     */
    @Transactional
    @Auditable(entity = "member", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        Member member = findManaged(uuid);
        long usages = countUsages(uuid);
        if (physical && usages == 0) {
            memberRepository.delete(member);
            return;
        }
        member.setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Member findManaged(UUID uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
    }

    /**
     * Wraps {@link MemberMapper#toDetail} with the three count fields the
     * mapper ignores. Kept here (not in the mapper) so the repos can stay
     * out of the mapper's dependency graph.
     */
    private MemberDetailDto toDetailWithCounts(Member member) {
        int beneficiariesCount = (int) beneficiaryRepository.countByMemberIdAndActiveTrue(member.getId());
        int documentsCount     = (int) documentRepository.countByMemberIdAndActiveTrue(member.getId());
        boolean hasMedicalRecord = medicalRecordRepository.existsByPersonId(member.getPerson().getId());

        MemberDetailDto base = mapper.toDetail(member);
        return new MemberDetailDto(
                base.uuid(),
                base.personUuid(),
                base.firstName(), base.middleName(), base.lastName(), base.secondLastName(), base.fullName(),
                base.documentType(), base.documentNumber(),
                base.taxDocumentType(), base.taxDocumentNumber(),
                base.birthDate(), base.gender(), base.maritalStatus(),
                base.birthplace(), base.numberOfChildren(), base.spouseName(),
                base.phone(), base.landlinePhone(), base.email(), base.locale(),
                base.address(), base.city(),
                base.occupation(), base.employerName(), base.jobPosition(), base.employerAddress(),
                base.enrolledAt(), base.notes(),
                beneficiariesCount, documentsCount, hasMedicalRecord,
                base.currentPromoterUuid(), base.currentPromoterName(),
                base.active(), base.status(), base.createdAt(), base.updatedAt(), base.confirmedAt()
        );
    }

    private Occupation resolveOccupation(UUID uuid) {
        if (uuid == null) return null;
        return occupationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("occupation.not_found"));
    }

    private Gender resolveGender(UUID uuid) {
        if (uuid == null) return null;
        return genderRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("gender.not_found"));
    }

    private MaritalStatus resolveMaritalStatus(UUID uuid) {
        if (uuid == null) return null;
        return maritalStatusRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("marital_status.not_found"));
    }

    private City resolveCity(UUID uuid) {
        if (uuid == null) return null;
        return cityRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("city.not_found"));
    }

    private static Specification<Member> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
