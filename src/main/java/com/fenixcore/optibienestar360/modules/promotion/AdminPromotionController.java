package com.fenixcore.optibienestar360.modules.promotion;

import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Campaign promotions (V175, hub ADR 0018): listed and created under their campaign, edited by uuid. */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionService promotionService;

    @GetMapping("/campaigns/{campaignUuid}/promotions")
    @PreAuthorize("hasAuthority('PROMOTION_VIEW_ALL')")
    public ResponseEntity<List<PromotionDto>> list(@PathVariable UUID campaignUuid) {
        return ResponseEntity.ok(promotionService.listForCampaign(campaignUuid));
    }

    @PostMapping("/campaigns/{campaignUuid}/promotions")
    @PreAuthorize("hasAuthority('PROMOTION_CREATE')")
    public ResponseEntity<PromotionDto> create(@PathVariable UUID campaignUuid,
                                               @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.create(campaignUuid, request));
    }

    @GetMapping("/promotions/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTION_VIEW_ALL')")
    public ResponseEntity<PromotionDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(promotionService.get(uuid));
    }

    @PutMapping("/promotions/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTION_UPDATE')")
    public ResponseEntity<PromotionDto> update(@PathVariable UUID uuid, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(promotionService.update(uuid, request));
    }

    @DeleteMapping("/promotions/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTION_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        promotionService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
