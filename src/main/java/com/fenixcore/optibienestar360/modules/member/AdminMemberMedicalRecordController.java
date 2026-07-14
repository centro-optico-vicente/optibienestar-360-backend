package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordUpsertRequest;
import com.fenixcore.optibienestar360.modules.member.service.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/members/{memberUuid}/medical-record} —
 * 1:1 with the member's person.
 *
 * <p>Three endpoints (no list, no /uuid path — there's exactly one record
 * per person):</p>
 * <ul>
 *   <li>{@code GET}     — always returns 200 with a DTO when the member
 *       exists. The DTO's {@code exists} flag is {@code true} when a real
 *       row is returned, {@code false} for the "no history yet" case (no
 *       row, or the row was soft-deleted). 404 is reserved for a
 *       {@code memberUuid} that doesn't resolve — that's a real
 *       "resource missing" error.</li>
 *   <li>{@code PUT}     — upsert: creates on first call, updates on
 *       subsequent calls with PATCH-style field merging. Reactivates
 *       soft-deleted rows.</li>
 *   <li>{@code DELETE}  — soft-deletes (the row stays for audit; subsequent
 *       GET returns the empty DTO with {@code exists=false}; PUT
 *       reactivates).</li>
 * </ul>
 *
 * <p><b>PRIVACY:</b> guarded by the V6-seeded {@code MEDICAL_RECORD_VIEW}
 * (read) and {@code MEDICAL_RECORD_UPDATE} (write) permissions, granted
 * only to SYSTEM / ADMINISTRADOR / OPERADOR_MEDICO. Ally users cannot reach
 * these surfaces — enforced both by the {@code /v1/admin} URL prefix
 * (allies don't carry admin perms) and explicitly by {@code @PreAuthorize}
 * for defense in depth.</p>
 */
@RestController
@RequestMapping("/v1/admin/members/{memberUuid}/medical-record")
@RequiredArgsConstructor
public class AdminMemberMedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEDICAL_RECORD_VIEW')")
    public ResponseEntity<MedicalRecordDto> get(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(medicalRecordService.getForMember(memberUuid));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('MEDICAL_RECORD_UPDATE')")
    public ResponseEntity<MedicalRecordDto> upsert(@PathVariable UUID memberUuid,
                                                   @Valid @RequestBody MedicalRecordUpsertRequest request) {
        return ResponseEntity.ok(medicalRecordService.upsert(memberUuid, request));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('MEDICAL_RECORD_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID memberUuid) {
        medicalRecordService.delete(memberUuid);
        return ResponseEntity.noContent().build();
    }
}
