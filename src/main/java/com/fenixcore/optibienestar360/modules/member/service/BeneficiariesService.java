package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-side CRUD for {@link Beneficiary} rows under a parent
 * {@link Member}. Reuses the {@link PersonService#findOrCreate} flow so a
 * person who is already a User / Member titular / beneficiary of another
 * titular never gets a duplicate persons row when added here.
 *
 * <p>Three invariants enforced (mirroring V18):</p>
 * <ol>
 *   <li><b>Membership scope</b> — every read / write resolves the parent
 *       member from the path AND verifies the child beneficiary actually
 *       belongs to it. Defends against URL tampering where
 *       {@code /members/A/beneficiaries/X} might point at a beneficiary of
 *       member B (404 instead of silently mutating the wrong member).</li>
 *   <li><b>UNIQUE (member_id, person_id)</b> — re-adding an existing
 *       (member, person) pair reactivates the soft-deleted row instead of
 *       letting the DB throw a 409 from the V18 unique index. Implements
 *       the readmission flow noted in the V18 migration comments.</li>
 *   <li><b>Plan-based cap (deferred)</b> — {@code plan.max_beneficiaries}
 *       enforcement was the third leg of the validations bullet. Plan
 *       doesn't exist yet (V20 planned), so this service does NOT check
 *       the cap; when Plan lands, add the check before
 *       {@code beneficiaryRepository.save(...)} in {@link #add}.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeneficiariesService {

    private final MemberRepository memberRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final PersonService personService;
    private final MemberMapper mapper;

    public List<BeneficiaryDto> listForMember(UUID memberUuid) {
        Member member = findMember(memberUuid);
        return beneficiaryRepository.findByMemberIdAndActiveTrue(member.getId()).stream()
                .map(mapper::toBeneficiaryDto)
                .toList();
    }

    public BeneficiaryDto get(UUID memberUuid, UUID beneficiaryUuid) {
        return mapper.toBeneficiaryDto(findUnderMember(memberUuid, beneficiaryUuid));
    }

    @Transactional
    public BeneficiaryDto add(UUID memberUuid, BeneficiaryCreateRequest req) {
        Member member = findMember(memberUuid);

        Person personSeed = new Person();
        personSeed.setFirstName(req.firstName());
        personSeed.setMiddleName(req.middleName());
        personSeed.setLastName(req.lastName());
        personSeed.setSecondLastName(req.secondLastName());
        personSeed.setDocumentType(req.documentType());
        personSeed.setDocumentNumber(req.documentNumber());
        personSeed.setBirthDate(req.birthDate());
        personSeed.setPhone(req.phone());
        personSeed.setEmail(req.email());
        Person person = personService.findOrCreate(personSeed);

        // UNIQUE(member_id, person_id) on V18 means re-adding an existing
        // pair would 409. The readmission contract is: reactivate the
        // existing row instead of inserting another.
        Optional<Beneficiary> existing =
                beneficiaryRepository.findByMemberIdAndPersonId(member.getId(), person.getId());

        Beneficiary beneficiary = existing.orElseGet(Beneficiary::new);
        beneficiary.setMember(member);
        beneficiary.setPerson(person);
        beneficiary.setRelationship(req.relationship());
        if (req.extraInscriptionPaid() != null) {
            beneficiary.setExtraInscriptionPaid(req.extraInscriptionPaid());
        }
        beneficiary.setActive(true);

        // TODO: when V20 plans lands, enforce plan.max_beneficiaries here:
        // count active beneficiaries of the member's plan and reject if
        // adding this one exceeds the cap (422 member.beneficiary.cap_exceeded).

        return mapper.toBeneficiaryDto(beneficiaryRepository.save(beneficiary));
    }

    @Transactional
    public BeneficiaryDto update(UUID memberUuid, UUID beneficiaryUuid, BeneficiaryUpdateRequest req) {
        Beneficiary beneficiary = findUnderMember(memberUuid, beneficiaryUuid);
        Person person = beneficiary.getPerson();

        if (req.firstName()        != null) person.setFirstName(req.firstName());
        if (req.middleName()       != null) person.setMiddleName(req.middleName());
        if (req.lastName()         != null) person.setLastName(req.lastName());
        if (req.secondLastName()   != null) person.setSecondLastName(req.secondLastName());
        if (req.documentType()     != null) person.setDocumentType(req.documentType());
        if (req.documentNumber()   != null) person.setDocumentNumber(req.documentNumber());
        if (req.birthDate()        != null) person.setBirthDate(req.birthDate());
        if (req.phone()            != null) person.setPhone(req.phone());
        if (req.email()            != null) person.setEmail(req.email());

        if (req.relationship()           != null) beneficiary.setRelationship(req.relationship());
        if (req.extraInscriptionPaid()   != null) beneficiary.setExtraInscriptionPaid(req.extraInscriptionPaid());
        if (req.inscriptionPaymentId()   != null) beneficiary.setInscriptionPaymentId(req.inscriptionPaymentId());
        if (req.active()                 != null) beneficiary.setActive(req.active());
        if (req.status()                 != null) beneficiary.setStatus(req.status());

        return mapper.toBeneficiaryDto(beneficiary);  // managed → dirty-check on commit
    }

    @Transactional
    public void delete(UUID memberUuid, UUID beneficiaryUuid) {
        Beneficiary beneficiary = findUnderMember(memberUuid, beneficiaryUuid);
        beneficiary.setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Member findMember(UUID uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
    }

    private Beneficiary findUnderMember(UUID memberUuid, UUID beneficiaryUuid) {
        Beneficiary beneficiary = beneficiaryRepository.findByUuid(beneficiaryUuid)
                .orElseThrow(() -> new NoSuchElementException("beneficiary.not_found"));
        if (!beneficiary.getMember().getUuid().equals(memberUuid)) {
            throw new NoSuchElementException("beneficiary.not_found");
        }
        return beneficiary;
    }
}
