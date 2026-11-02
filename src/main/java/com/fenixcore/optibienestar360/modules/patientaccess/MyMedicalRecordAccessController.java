package com.fenixcore.optibienestar360.modules.patientaccess;

import com.fenixcore.optibienestar360.modules.patientaccess.dto.AllyPatientLinkDto;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.MyMedicalRecordAccessDto;
import com.fenixcore.optibienestar360.modules.patientaccess.service.PatientAccessService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Member side (hub ADR 0032): who can read my medical history, who read it, and revoking a link. */
@RestController
@RequestMapping("/v1/me/medical-record")
@RequiredArgsConstructor
public class MyMedicalRecordAccessController {

    private final PatientAccessService patientAccessService;

    @GetMapping("/access")
    @PreAuthorize("hasAuthority('ALLY_PATIENT_LINK_VIEW_OWN')")
    public ResponseEntity<MyMedicalRecordAccessDto> access(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(patientAccessService.myAccess(actor.getUuid()));
    }

    @DeleteMapping("/links/{linkUuid}")
    @PreAuthorize("hasAuthority('ALLY_PATIENT_LINK_REVOKE_OWN')")
    public ResponseEntity<AllyPatientLinkDto> revoke(@PathVariable UUID linkUuid,
                                                     @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(patientAccessService.revoke(linkUuid, actor.getUuid()));
    }
}
