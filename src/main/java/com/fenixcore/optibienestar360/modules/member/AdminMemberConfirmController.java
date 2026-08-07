package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.MemberConfirmationDto;
import com.fenixcore.optibienestar360.modules.member.service.MemberConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Manual member confirmation (project chat 2026-08-06). Separate controller
 * from {@code AdminMemberController} / {@code AdminMemberPromoterController}
 * so the distinct permission ({@code MEMBER_CONFIRM}) stays visibly segregated,
 * mirroring the {@code AdminMemberPromoterController} pattern.
 */
@RestController
@RequestMapping("/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberConfirmController {

    private final MemberConfirmationService memberConfirmationService;

    @PostMapping("/{memberUuid}/confirm")
    @PreAuthorize("hasAuthority('MEMBER_CONFIRM')")
    public ResponseEntity<MemberConfirmationDto> confirm(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(new MemberConfirmationDto(memberUuid, memberConfirmationService.confirm(memberUuid)));
    }
}
