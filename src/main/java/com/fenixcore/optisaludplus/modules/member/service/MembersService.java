package com.fenixcore.optisaludplus.modules.member.service;

import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import com.fenixcore.optisaludplus.modules.catalog.entity.Gender;
import com.fenixcore.optisaludplus.modules.catalog.entity.MaritalStatus;
import com.fenixcore.optisaludplus.modules.catalog.entity.Occupation;
import com.fenixcore.optisaludplus.modules.catalog.repository.CityRepository;
import com.fenixcore.optisaludplus.modules.catalog.repository.GenderRepository;
import com.fenixcore.optisaludplus.modules.catalog.repository.MaritalStatusRepository;
import com.fenixcore.optisaludplus.modules.catalog.repository.OccupationRepository;
import com.fenixcore.optisaludplus.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optisaludplus.modules.member.dto.MemberDetailDto;
import com.fenixcore.optisaludplus.modules.member.dto.MemberListItemDto;
import com.fenixcore.optisaludplus.modules.member.dto.MemberUpdateRequest;
import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.member.mapper.MemberMapper;
import com.fenixcore.optisaludplus.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optisaludplus.modules.member.repository.MemberDocumentRepository;
import com.fenixcore.optisaludplus.modules.member.repository.MemberRepository;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.person.service.PersonService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
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

    private final MemberRepository memberRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final MemberDocumentRepository documentRepository;
    private final PersonService personService;
    private final OccupationRepository occupationRepository;
    private final GenderRepository genderRepository;
    private final MaritalStatusRepository maritalStatusRepository;
    private final CityRepository cityRepository;
    private final MemberMapper mapper;

    // ─── Read ───────────────────────────────────────────────────────────────

    public MemberDetailDto getDetail(UUID uuid) {
        return toDetailWithCounts(findManaged(uuid));
    }

    public Page<MemberListItemDto> list(Pageable pageable, String filter, String q) {
        Specification<Member> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "member.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return memberRepository.findAll(spec, pageable).map(mapper::toListItem);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    public MemberDetailDto create(MemberCreateRequest req) {
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
        personSeed.setPhone(req.phone());
        personSeed.setEmail(req.email());
        personSeed.setLocale(req.locale());
        personSeed.setAddress(req.address());
        personSeed.setCity(resolveCity(req.cityUuid()));

        // findOrCreate dedupes by (document_type, document_number). When the
        // person already exists (e.g. they were a User or a Beneficiary of
        // another titular first), the seed payload is ignored and the
        // existing managed row is returned — admin must update the person
        // explicitly via PUT to change demographics.
        Person person = personService.findOrCreate(personSeed);

        // Member-side dedupe — UNIQUE(person_id) on V17 would catch it
        // anyway, but pre-checking lets us return a clean 422 instead of
        // the misleading 409 from the unique violation.
        if (memberRepository.existsByPersonId(person.getId())) {
            throw new IllegalArgumentException("member.person.already_enrolled");
        }

        Member member = new Member();
        member.setPerson(person);
        member.setOccupation(resolveOccupation(req.occupationUuid()));
        if (req.enrolledAt() != null) {
            member.setEnrolledAt(req.enrolledAt());
        }
        member.setNotes(req.notes());

        Member saved = memberRepository.save(member);
        return toDetailWithCounts(saved);
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Transactional
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
        if (req.phone()             != null) person.setPhone(req.phone());
        if (req.email()             != null) person.setEmail(req.email());
        if (req.locale()            != null) person.setLocale(req.locale());
        if (req.address()           != null) person.setAddress(req.address());
        if (req.cityUuid()          != null) person.setCity(resolveCity(req.cityUuid()));

        // Member-side updates.
        if (req.occupationUuid() != null) member.setOccupation(resolveOccupation(req.occupationUuid()));
        if (req.enrolledAt()     != null) member.setEnrolledAt(req.enrolledAt());
        if (req.notes()          != null) member.setNotes(req.notes());
        if (req.active()         != null) member.setActive(req.active());
        if (req.status()         != null) member.setStatus(req.status());

        return toDetailWithCounts(member);
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID uuid) {
        Member member = findManaged(uuid);
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
        // MedicalRecord lookup not implemented yet; vertical-4 bullet for
        // /v1/admin/members/{id}/medical-record will own that surface.
        boolean hasMedicalRecord = false;

        MemberDetailDto base = mapper.toDetail(member);
        return new MemberDetailDto(
                base.uuid(),
                base.personUuid(),
                base.firstName(), base.middleName(), base.lastName(), base.secondLastName(), base.fullName(),
                base.documentType(), base.documentNumber(),
                base.taxDocumentType(), base.taxDocumentNumber(),
                base.birthDate(), base.gender(), base.maritalStatus(),
                base.phone(), base.email(), base.locale(),
                base.address(), base.city(),
                base.occupation(), base.enrolledAt(), base.notes(),
                beneficiariesCount, documentsCount, hasMedicalRecord,
                base.active(), base.status(), base.createdAt(), base.updatedAt()
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
