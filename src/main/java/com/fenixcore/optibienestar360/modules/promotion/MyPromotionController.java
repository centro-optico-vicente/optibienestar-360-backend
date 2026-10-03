package com.fenixcore.optibienestar360.modules.promotion;

import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipPromotionStatusDto;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionAssignmentService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The affiliate's own active promotion, for the member portal (hub ADR 0018). */
@RestController
@RequestMapping("/v1/me/promotion")
@RequiredArgsConstructor
public class MyPromotionController {

    private final PromotionAssignmentService assignmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<MembershipPromotionStatusDto> mine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(assignmentService.currentForUser(actor.getUuid()));
    }
}
