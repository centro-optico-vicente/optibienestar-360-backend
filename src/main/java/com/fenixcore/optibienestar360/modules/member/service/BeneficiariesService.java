package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.service.BeneficiaryInscriptionBiller;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 *   <li><b>Plan-based cap + extra-inscription billing (v2)</b> — the
 *       titular's active membership plan drives two rules on {@link #add}:
 *       a hard cap ({@code plan.max_beneficiaries}, {@code null} = no cap →
 *       Corporativo) rejected with 422 {@code member.beneficiary.cap_exceeded},
 *       and a one-time extra inscription fee
 *       ({@code plan.extra_beneficiary_inscription_fee}) charged when the new
 *       beneficiary lands beyond {@code plan.included_beneficiaries}. The
 *       charge is a PENDING inscription payment linked to the beneficiary; the
 *       monthly fee never depends on the beneficiary count.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeneficiariesService {

    private final MemberRepository memberRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final MembershipRepository membershipRepository;
    private final PersonService personService;
    private final BeneficiaryInscriptionBiller inscriptionBiller;
    private final SubsidyResolver subsidyResolver;
    private final MemberMapper mapper;

    public List<BeneficiaryDto> listForMember(UUID memberUuid) {
        Member member = findMember(memberUuid);
        return beneficiaryRepository.findByMemberIdAndActiveTrue(member.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Self-service "my family" ({@code GET /v1/me/family}) — the beneficiaries
     * of the JWT-authenticated user's own member record. Resolves the member via
     * the {@code user.person → member.person} link; a user who is not an active
     * affiliate 404s with {@code me.member.not_enrolled}, same contract as
     * {@code GET /v1/me/member}.
     */
    public List<BeneficiaryDto> listForUser(UUID userUuid) {
        Member member = memberRepository.findByUserUuid(userUuid)
                .filter(Member::isActive)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));
        return beneficiaryRepository.findByMemberIdAndActiveTrue(member.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public BeneficiaryDto get(UUID memberUuid, UUID beneficiaryUuid) {
        return toDto(findUnderMember(memberUuid, beneficiaryUuid));
    }

    @Transactional
    public BeneficiaryDto add(UUID memberUuid, BeneficiaryCreateRequest req) {
        Member member = findMember(memberUuid);

        // A beneficiary is covered under the titular's plan — resolve it from
        // the active membership. Without one there is no plan to price the cap
        // or the extra inscription against, so adding is rejected.
        Membership membership = membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("member.beneficiary.no_active_membership"));
        Plan plan = membership.getPlan();

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

        // A brand-new row, or the reactivation of a soft-deleted one, consumes a
        // new active slot; an idempotent re-add of an already-active beneficiary
        // does not — so it triggers neither the cap nor a second charge. Derive
        // this from the existing row's state (a fresh entity defaults to active
        // via BaseEntity, so we cannot read it off the target entity).
        boolean consumesNewSlot = existing.map(b -> !b.isActive()).orElse(true);
        Beneficiary beneficiary = existing.orElseGet(Beneficiary::new);

        long activeCount = beneficiaryRepository.countByMemberIdAndActiveTrue(member.getId());

        // Hard cap (max_beneficiaries NULL = no cap, e.g. Corporativo).
        if (consumesNewSlot && plan.getMaxBeneficiaries() != null
                && activeCount >= plan.getMaxBeneficiaries()) {
            throw new IllegalArgumentException("member.beneficiary.cap_exceeded");
        }

        beneficiary.setMember(member);
        beneficiary.setPerson(person);
        beneficiary.setRelationship(req.relationship());
        if (req.extraInscriptionPaid() != null) {
            beneficiary.setExtraInscriptionPaid(req.extraInscriptionPaid());
        }
        beneficiary.setActive(true);

        // A subsidy may fully exonerate this beneficiary's extra inscription
        // (v2 subsidies, V41). Only meaningful on reactivation of an existing
        // beneficiary already listed under an active subsidy — a brand-new row
        // has no id yet, so no subsidy line can reference it (returns false).
        boolean inscriptionExonerated = beneficiary.getId() != null
                && subsidyResolver.beneficiaryInscriptionExonerated(beneficiary.getId(), LocalDate.now());

        // Extra inscription: charged once when this new slot lands beyond the
        // plan's included cap and it hasn't been settled already (admin marked
        // it paid, a prior charge is still linked, or a subsidy exonerates it).
        boolean beyondIncluded = activeCount >= plan.getIncludedBeneficiaries();
        boolean alreadySettled = beneficiary.isExtraInscriptionPaid()
                || beneficiary.getInscriptionPaymentId() != null;
        BigDecimal fee = plan.getExtraBeneficiaryInscriptionFee();
        if (inscriptionExonerated) {
            beneficiary.setExtraInscriptionPaid(true);   // covered by the subsidy, no charge
        } else if (consumesNewSlot && beyondIncluded && !alreadySettled
                && fee != null && fee.signum() > 0) {
            beneficiary.setInscriptionPaymentId(inscriptionBiller.chargeExtraInscription(membership, fee));
            beneficiary.setExtraInscriptionPaid(false);   // PENDING until the charge is approved
        }

        return toDto(beneficiaryRepository.save(beneficiary));
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
        if (req.active()                 != null) beneficiary.setActive(req.active());
        if (req.status()                 != null) beneficiary.setStatus(req.status());

        return toDto(beneficiary);  // managed → dirty-check on commit
    }

    @Transactional
    public void delete(UUID memberUuid, UUID beneficiaryUuid) {
        Beneficiary beneficiary = findUnderMember(memberUuid, beneficiaryUuid);
        beneficiary.setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Maps a beneficiary, resolving its linked inscription payment id to the external UUID. */
    private BeneficiaryDto toDto(Beneficiary beneficiary) {
        return mapper.toBeneficiaryDto(beneficiary, inscriptionBiller.resolveUuid(beneficiary.getInscriptionPaymentId()));
    }

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
