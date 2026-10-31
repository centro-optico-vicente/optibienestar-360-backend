package com.fenixcore.optibienestar360.modules.patientaccess;

import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.AllyPatientLinkDto;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.PatientAccessEligibilityDto;
import com.fenixcore.optibienestar360.modules.patientaccess.service.PatientAccessService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ally side of the patient medical history (hub ADR 0032). The permission
 * only lets the request in; {@link PatientAccessService} narrows it to the
 * holder person or an OPERADOR_MEDICO of a B2P medical ally, with an active
 * doctor–patient link for reads. The medical record is never cached.
 */
@RestController
@RequestMapping("/v1/me/allies/{allyUuid}/patients")
@RequiredArgsConstructor
public class AllyPatientController {

    private final PatientAccessService patientAccessService;

    /** Whether to offer "Ver historia médica" at this ally. */
    @GetMapping("/eligibility")
    @PreAuthorize("hasAuthority('MEMBER_MEDICAL_RECORD_VIEW_PATIENT')")
    public ResponseEntity<PatientAccessEligibilityDto> eligibility(@PathVariable UUID allyUuid,
                                                                   @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(patientAccessService.eligibility(allyUuid, actor.getUuid()));
    }

    /** "Mis pacientes". */
    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_MEDICAL_RECORD_VIEW_PATIENT')")
    public ResponseEntity<Page<AllyPatientLinkDto>> patients(@PathVariable UUID allyUuid,
                                                             @PageableDefault(size = 20) Pageable pageable,
                                                             @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(patientAccessService.patients(allyUuid, actor.getUuid(), pageable));
    }

    /** Opens the link after a QR scan of the member's credential (never for a typed document). */
    @PostMapping("/{memberUuid}/links")
    @PreAuthorize("hasAuthority('MEMBER_MEDICAL_RECORD_VIEW_PATIENT')")
    public ResponseEntity<AllyPatientLinkDto> openLink(@PathVariable UUID allyUuid,
                                                       @PathVariable UUID memberUuid,
                                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(patientAccessService.openLink(allyUuid, memberUuid, actor.getUuid()));
    }

    @GetMapping("/{memberUuid}/medical-record")
    @PreAuthorize("hasAuthority('MEMBER_MEDICAL_RECORD_VIEW_PATIENT')")
    public ResponseEntity<MedicalRecordDto> medicalRecord(@PathVariable UUID allyUuid,
                                                          @PathVariable UUID memberUuid,
                                                          @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(patientAccessService.readMedicalRecord(allyUuid, memberUuid, actor.getUuid()));
    }
}
