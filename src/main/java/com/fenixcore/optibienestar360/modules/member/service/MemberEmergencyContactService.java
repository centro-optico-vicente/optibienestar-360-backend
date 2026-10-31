package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.member.dto.EmergencyContactDto;
import com.fenixcore.optibienestar360.modules.member.dto.EmergencyContactUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MedicalRecordRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Reads and writes ONLY the emergency contact of a member's medical record
 * (V212, hub ADR 0031 §14). The rest of the medical record stays behind
 * {@code MEDICAL_RECORD_*} ({@link MedicalRecordService}).
 *
 * <ul>
 *   <li><b>Staff</b> ({@code MEMBER_EMERGENCY_CONTACT_VIEW_ALL} / {@code _UPDATE}): any member.</li>
 *   <li><b>Advisor</b> ({@code _UPDATE_DOWNLINE}): members of their own portfolio only. A member
 *       outside it answers the same 404 as a missing one, like the advisor's collections.</li>
 *   <li><b>Affiliate</b> ({@code _VIEW_OWN} / {@code _UPDATE_OWN}): their own member record.</li>
 * </ul>
 *
 * <p>When the person has no medical record yet, a write creates one holding only the
 * contact. A soft-deleted record keeps its soft-delete: writing the contact must not
 * resurrect clinical data that someone with medical permissions chose to hide.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberEmergencyContactService {

    private final MemberRepository memberRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PromoterRepository promoterRepository;

    // ─── Staff ──────────────────────────────────────────────────────────────

    public EmergencyContactDto get(UUID memberUuid) {
        return read(findMember(memberUuid));
    }

    @Transactional
    @Auditable(entity = "medical_record", action = AuditAction.UPDATE)
    public EmergencyContactDto update(UUID memberUuid, EmergencyContactUpdateRequest request) {
        return write(findMember(memberUuid), request);
    }

    // ─── Advisor (own portfolio) ────────────────────────────────────────────

    public EmergencyContactDto getForAdvisor(UUID actorUserUuid, UUID memberUuid) {
        return read(portfolioMember(actorUserUuid, memberUuid));
    }

    @Transactional
    @Auditable(entity = "medical_record", action = AuditAction.UPDATE)
    public EmergencyContactDto updateForAdvisor(UUID actorUserUuid, UUID memberUuid, EmergencyContactUpdateRequest request) {
        return write(portfolioMember(actorUserUuid, memberUuid), request);
    }

    // ─── Affiliate (own record) ─────────────────────────────────────────────

    public EmergencyContactDto getOwn(UUID actorUserUuid) {
        return read(ownMember(actorUserUuid));
    }

    @Transactional
    @Auditable(entity = "medical_record", action = AuditAction.UPDATE)
    public EmergencyContactDto updateOwn(UUID actorUserUuid, EmergencyContactUpdateRequest request) {
        return write(ownMember(actorUserUuid), request);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private EmergencyContactDto read(Member member) {
        return medicalRecordRepository.findByPersonId(member.getPerson().getId())
                .map(MemberEmergencyContactService::toDto)
                .orElseGet(EmergencyContactDto::empty);
    }

    private EmergencyContactDto write(Member member, EmergencyContactUpdateRequest request) {
        MedicalRecord record = medicalRecordRepository.findByPersonId(member.getPerson().getId())
                .orElseGet(() -> {
                    MedicalRecord fresh = new MedicalRecord();
                    fresh.setPerson(member.getPerson());
                    return fresh;
                });
        record.setEmergencyContactName(blankToNull(request.name()));
        record.setEmergencyContactPhone(blankToNull(request.phone()));
        record.setEmergencyContactRelationship(request.relationship());
        return toDto(medicalRecordRepository.save(record));
    }

    private Member findMember(UUID memberUuid) {
        return memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
    }

    private Member portfolioMember(UUID actorUserUuid, UUID memberUuid) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        Member member = findMember(memberUuid);
        Promoter owner = member.getPromoter();
        if (owner == null || !owner.getId().equals(promoter.getId())) {
            throw new NoSuchElementException("promoter.member.not_in_portfolio");
        }
        return member;
    }

    private Member ownMember(UUID actorUserUuid) {
        return memberRepository.findByUserUuid(actorUserUuid)
                .filter(Member::isActive)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));
    }

    private static EmergencyContactDto toDto(MedicalRecord record) {
        boolean exists = record.getEmergencyContactName() != null
                || record.getEmergencyContactPhone() != null
                || record.getEmergencyContactRelationship() != null;
        return new EmergencyContactDto(record.getEmergencyContactName(), record.getEmergencyContactPhone(),
                record.getEmergencyContactRelationship(), exists);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
