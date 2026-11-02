package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.CreditHoldRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.service.MemberCreditHoldService;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Freeze or release the automatic use of a member's credit (V205); answers the refreshed member detail. */
@RestController
@RequestMapping("/v1/admin/members/{uuid}/credit-hold")
@RequiredArgsConstructor
public class AdminMemberCreditHoldController {

    private final MemberCreditHoldService holdService;
    private final MembersService membersService;

    @PutMapping
    @PreAuthorize("hasAuthority('PERSON_CREDIT_HOLD_UPDATE')")
    public ResponseEntity<MemberDetailDto> setHold(@PathVariable UUID uuid,
                                                   @Valid @RequestBody CreditHoldRequest request,
                                                   @AuthenticationPrincipal CustomUserDetails actor) {
        holdService.setHold(uuid, request, actor.getUuid());
        return ResponseEntity.ok(membersService.getDetail(uuid));
    }
}
