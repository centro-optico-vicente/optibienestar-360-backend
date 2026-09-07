package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.AssignSupervisorRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterSupervisorAssignmentDto;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterHierarchyService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin reassignment of a promoter's supervisor (V101, hub plan §1). Separate
 * controller from {@code AdminPromoterController} so the distinct permission
 * ({@code PROMOTER_ASSIGN_SUPERVISOR}, gates only this action) and the audit
 * semantics stay visibly segregated from plain promoter CRUD — mirrors
 * {@code AdminMemberPromoterController} for the member↔promoter link (V35).
 */
@RestController
@RequestMapping("/v1/admin/promoters")
@RequiredArgsConstructor
public class AdminPromoterHierarchyController {

    private final PromoterHierarchyService hierarchyService;

    @PostMapping("/{promoterUuid}/assign-supervisor")
    @PreAuthorize("hasAuthority('PROMOTER_ASSIGN_SUPERVISOR')")
    public ResponseEntity<PromoterSupervisorAssignmentDto> assignSupervisor(
            @PathVariable UUID promoterUuid,
            @Valid @RequestBody AssignSupervisorRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(hierarchyService.assignSupervisor(
                promoterUuid, request.supervisorUuid(), request.reason(), actor.getUuid()));
    }

    @GetMapping("/{promoterUuid}/supervisor-history")
    @PreAuthorize("hasAuthority('PROMOTER_ASSIGN_SUPERVISOR')")
    public ResponseEntity<List<PromoterSupervisorAssignmentDto>> supervisorHistory(
            @PathVariable UUID promoterUuid) {
        return ResponseEntity.ok(hierarchyService.history(promoterUuid));
    }
}
