package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordUpsertRequest;
import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.MedicalRecordRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service for the per-member medical record sub-resource.
 *
 * <p>The record itself is 1:1 with {@link
 * com.fenixcore.optibienestar360.modules.person.entity.Person Person}, not
 * with {@link Member} — same record covers the human whether they're a
 * titular or a beneficiary. This service is mounted under the member route
 * for UX (admin lands on a member's ficha and clicks "historial médico"),
 * but internally resolves {@code member.person.id} and operates on the
 * underlying persons-scoped row.</p>
 *
 * <p>The upsert semantics on {@link #upsert} keep the frontend simple —
 * one form for both first-time creation and subsequent edits, no separate
 * POST endpoint, no "does it exist already?" pre-flight needed.</p>
 *
 * <p><b>PRIVACY:</b> ally users must NEVER receive a MedicalRecordDto via
 * API. The route is guarded by the V6-seeded {@code MEDICAL_RECORD_VIEW}
 * and {@code MEDICAL_RECORD_UPDATE} permissions, granted only to SYSTEM /
 * ADMINISTRADOR / OPERADOR_MEDICO. Documented in the V19 migration comment
 * and the entity javadoc.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicalRecordService {

    private final MemberRepository memberRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MemberMapper mapper;

    /**
     * Get the medical record of the member's person.
     *
     * <p><b>Contract:</b> always returns 200 with a DTO when the member
     * exists. The DTO's {@code exists} flag distinguishes the two shapes:
     * {@code true} when a real row is returned, {@code false} for the
     * "no history yet" case (member has no {@code medical_records} row,
     * or the row was soft-deleted). Frontends key their empty-state vs
     * populated-form branch on {@code exists}, not on HTTP status.</p>
     *
     * <p>404 stays reserved for a {@code memberUuid} that doesn't resolve
     * to any member — that's a real "resource missing" error, distinct
     * from "resource exists conceptually but has no data yet".</p>
     *
     * <p>This behavior applies the project-wide convention defined in
     * {@code .ai/decisions/0012-empty-state-http-conventions.md}
     * (sub-resource 1:1 → 200 with {@code exists} flag, never 404 for
     * "empty yet"). New sub-resources should follow the same pattern.</p>
     */
    public MedicalRecordDto getForMember(UUID memberUuid) {
        Member member = findMember(memberUuid);
        return medicalRecordRepository.findByPersonId(member.getPerson().getId())
                .filter(MedicalRecord::isActive)  // soft-deleted rows look like "no history" to GET
                .map(mapper::toMedicalRecordDto)
                .orElseGet(() -> emptyDto(member));
    }

    /**
     * Upsert: create the record when none exists for the person, update
     * the existing one otherwise. PATCH semantics within update — non-null
     * fields are applied, null fields keep the current (or {@code null} on
     * create) value.
     */
    @Transactional
    @Auditable(entity = "medical_record", action = AuditAction.UPDATE)
    public MedicalRecordDto upsert(UUID memberUuid, MedicalRecordUpsertRequest req) {
        Member member = findMember(memberUuid);
        MedicalRecord record = medicalRecordRepository.findByPersonId(member.getPerson().getId())
                .orElseGet(() -> {
                    MedicalRecord fresh = new MedicalRecord();
                    fresh.setPerson(member.getPerson());
                    return fresh;
                });
        // Reactivate the row if it had been soft-deleted — admin is
        // explicitly re-filling the record so the previous deletion should
        // not block visibility.
        record.setActive(true);

        if (req.bloodType()                      != null) record.setBloodType(req.bloodType());
        if (req.allergies()                      != null) record.setAllergies(req.allergies());
        if (req.chronicConditions()              != null) record.setChronicConditions(req.chronicConditions());
        if (req.currentMedications()             != null) record.setCurrentMedications(req.currentMedications());
        if (req.emergencyContactName()           != null) record.setEmergencyContactName(req.emergencyContactName());
        if (req.emergencyContactPhone()          != null) record.setEmergencyContactPhone(req.emergencyContactPhone());
        if (req.emergencyContactRelationship()   != null) record.setEmergencyContactRelationship(req.emergencyContactRelationship());
        if (req.notes()                          != null) record.setNotes(req.notes());

        return mapper.toMedicalRecordDto(medicalRecordRepository.save(record));
    }

    /**
     * Soft-delete the record. The row stays for audit; subsequent GETs
     * return the empty DTO ({@code exists=false}) because the soft-deleted
     * filter treats it as "no history". Admin can recreate via PUT, which
     * reactivates the existing row via {@code findByPersonId} hit.
     */
    @Transactional
    @Auditable(entity = "medical_record", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID memberUuid) {
        Member member = findMember(memberUuid);
        medicalRecordRepository.findByPersonId(member.getPerson().getId())
                .ifPresent(record -> record.setActive(false));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Member findMember(UUID uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
    }

    /**
     * Empty representation returned by {@link #getForMember} when the
     * member has no {@code medical_records} row (or the row is
     * soft-deleted). Carries the resolved {@code personUuid} so the
     * frontend can still correlate with person-scoped surfaces; every
     * clinical field is {@code null}, timestamps are {@code null}, and
     * {@code exists} is {@code false}. The record's own {@code uuid} is
     * {@code null} because there is no row to reference yet — it will be
     * generated on the first {@code PUT}.
     */
    private static MedicalRecordDto emptyDto(Member member) {
        return new MedicalRecordDto(
                null,                              // uuid — no row yet
                member.getPerson().getUuid(),      // personUuid — always known
                null, null, null, null,            // bloodType / allergies / chronicConditions / currentMedications
                null, null, null,                  // emergency contact triple
                null,                              // notes
                true,                              // active — no soft-delete yet
                null,                              // status
                null, null,                        // createdAt, updatedAt
                false                              // exists
        );
    }
}
