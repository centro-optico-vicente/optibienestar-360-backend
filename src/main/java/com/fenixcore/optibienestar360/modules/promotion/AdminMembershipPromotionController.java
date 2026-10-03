package com.fenixcore.optibienestar360.modules.promotion;

import com.fenixcore.optibienestar360.modules.promotion.dto.AssignPromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.dto.CancelPromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipPromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipPromotionStatusDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionAssignmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The promotion of a membership (hub ADR 0018): read, eligible options, apply and cancel. */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminMembershipPromotionController {

    private final PromotionAssignmentService assignmentService;

    @GetMapping("/memberships/{uuid}/promotion")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_VIEW_ALL', 'PROMOTION_VIEW_ALL')")
    public ResponseEntity<MembershipPromotionStatusDto> current(@PathVariable UUID uuid) {
        return ResponseEntity.ok(assignmentService.current(uuid));
    }

    @GetMapping("/memberships/{uuid}/promotion/options")
    @PreAuthorize("hasAuthority('PROMOTION_ASSIGN')")
    public ResponseEntity<List<PromotionDto>> options(@PathVariable UUID uuid) {
        return ResponseEntity.ok(assignmentService.options(uuid));
    }

    /** Promotions offered for a new enrollment on the given plan (used by the enrollment form). */
    @GetMapping("/promotions/offered")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_CREATE', 'PROMOTION_ASSIGN')")
    public ResponseEntity<List<PromotionDto>> offeredForEnrollment(@RequestParam UUID planUuid) {
        return ResponseEntity.ok(assignmentService.optionsForEnrollment(planUuid));
    }

    @PostMapping("/memberships/{uuid}/promotion")
    @PreAuthorize("hasAuthority('PROMOTION_ASSIGN')")
    public ResponseEntity<MembershipPromotionDto> assign(@PathVariable UUID uuid,
                                                         @Valid @RequestBody AssignPromotionRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(assignmentService.assignToExisting(
                uuid, request.promotionUuid(), request.code(), actor.getUuid()));
    }

    @PostMapping("/memberships/{uuid}/promotion/cancel")
    @PreAuthorize("hasAuthority('PROMOTION_ASSIGN')")
    public ResponseEntity<MembershipPromotionDto> cancel(@PathVariable UUID uuid,
                                                         @Valid @RequestBody CancelPromotionRequest request) {
        return ResponseEntity.ok(assignmentService.cancel(uuid, request.reason()));
    }
}
