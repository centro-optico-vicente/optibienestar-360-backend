package com.fenixcore.optibienestar360.modules.patientaccess;

import com.fenixcore.optibienestar360.modules.patientaccess.dto.MyMedicalRecordAccessDto;
import com.fenixcore.optibienestar360.modules.patientaccess.service.PatientAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Staff audit of a member's doctor–patient links and medical history reads (hub ADR 0032). */
@RestController
@RequestMapping("/v1/admin/members/{memberUuid}/patient-access")
@RequiredArgsConstructor
public class AdminPatientAccessController {

    private final PatientAccessService patientAccessService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_PATIENT_LINK_VIEW_ALL')")
    public ResponseEntity<MyMedicalRecordAccessDto> access(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(patientAccessService.accessOfMember(memberUuid));
    }
}
