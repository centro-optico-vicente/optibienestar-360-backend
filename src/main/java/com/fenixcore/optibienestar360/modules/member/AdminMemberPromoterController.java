package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.AssignPromoterRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberPromoterAssignmentDto;
import com.fenixcore.optibienestar360.modules.member.service.MemberPromoterService;
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
 * Admin reassignment of the permanent member↔promoter link (v2 PDF 2.a).
 * Separate controller from {@code AdminMemberController} so the distinct
 * permission ({@code MEMBER_ASSIGN_PROMOTER}, gates only this action) and the
 * audit semantics stay visibly segregated from plain member CRUD.
 */
@RestController
@RequestMapping("/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberPromoterController {

    private final MemberPromoterService memberPromoterService;

    @PostMapping("/{memberUuid}/assign-promoter")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<MemberPromoterAssignmentDto> assignPromoter(
            @PathVariable UUID memberUuid,
            @Valid @RequestBody AssignPromoterRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(memberPromoterService.assign(
                memberUuid, request.promoterUuid(), request.referralCode(), request.reason(), actor.getUuid()));
    }

    @GetMapping("/{memberUuid}/promoter-history")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<List<MemberPromoterAssignmentDto>> promoterHistory(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(memberPromoterService.history(memberUuid));
    }
}
