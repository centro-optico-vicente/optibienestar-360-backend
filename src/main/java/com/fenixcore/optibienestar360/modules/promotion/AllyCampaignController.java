package com.fenixcore.optibienestar360.modules.promotion;

import com.fenixcore.optibienestar360.modules.promotion.dto.AllyPartnershipCampaignDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.AllyPromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.service.AllyPartnershipService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Ally portal: campaigns open to partnership and the ally's own benefit lines
 * in them (V257, hub ADR 0038 §8). {@code allyUuid} picks which of the
 * caller's allies acts; omitted = the caller's primary ally.
 */
@RestController
@RequestMapping("/v1/me/allies")
@RequiredArgsConstructor
public class AllyCampaignController {

    private final AllyPartnershipService partnershipService;

    @GetMapping("/campaigns")
    @PreAuthorize("hasAuthority('ALLY_CAMPAIGN_VIEW')")
    public ResponseEntity<List<AllyPartnershipCampaignDto>> campaigns(
            @RequestParam(required = false) UUID allyUuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(partnershipService.campaigns(actor.getUuid(), allyUuid));
    }

    @GetMapping("/campaigns/{uuid}/promotions")
    @PreAuthorize("hasAuthority('ALLY_CAMPAIGN_VIEW')")
    public ResponseEntity<List<PromotionDto>> promotions(@PathVariable UUID uuid,
                                                         @RequestParam(required = false) UUID allyUuid,
                                                         @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(partnershipService.promotions(actor.getUuid(), allyUuid, uuid));
    }

    @PostMapping("/campaigns/{uuid}/promotions")
    @PreAuthorize("hasAuthority('ALLY_CAMPAIGN_PROMOTION_CREATE')")
    public ResponseEntity<PromotionDto> create(@PathVariable UUID uuid,
                                               @RequestParam(required = false) UUID allyUuid,
                                               @Valid @RequestBody AllyPromotionRequest request,
                                               @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(partnershipService.create(actor.getUuid(), allyUuid, uuid, request));
    }

    @PutMapping("/promotions/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_CAMPAIGN_PROMOTION_UPDATE')")
    public ResponseEntity<PromotionDto> update(@PathVariable UUID uuid,
                                               @RequestParam(required = false) UUID allyUuid,
                                               @Valid @RequestBody AllyPromotionRequest request,
                                               @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(partnershipService.update(actor.getUuid(), allyUuid, uuid, request));
    }

    @DeleteMapping("/promotions/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_CAMPAIGN_PROMOTION_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                       @RequestParam(required = false) UUID allyUuid,
                                       @AuthenticationPrincipal CustomUserDetails actor) {
        partnershipService.delete(actor.getUuid(), allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
