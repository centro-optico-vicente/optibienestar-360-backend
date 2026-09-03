package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.BonusAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusAwardsService;
import lombok.RequiredArgsConstructor;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only admin surface over the granted bonuses ledger (v2 PDF #5). The
 * canonical payout query {@code ?filter=status==PENDING} is backed by the V37
 * partial index {@code (status, created_at DESC)}.
 */
@RestController
@RequestMapping("/v1/admin/bonus-awards")
@RequiredArgsConstructor
public class AdminBonusAwardController {

    private final BonusAwardsService bonusAwardsService;

    @GetMapping
    @PreAuthorize("hasAuthority('BONUS_AWARD_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<BonusAwardDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        Page<BonusAwardDto> page = bonusAwardsService.list(pageable, filter, q);
        return ResponseEntity.ok(new AppliedSortPage<>(page, bonusAwardsService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('BONUS_AWARD_VIEW_ALL')")
    public ResponseEntity<BonusAwardDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(bonusAwardsService.get(uuid));
    }
}
