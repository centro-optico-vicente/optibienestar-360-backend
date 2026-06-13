package com.fenixcore.optisaludplus.modules.promoter;

import com.fenixcore.optisaludplus.modules.promoter.dto.CommissionDto;
import com.fenixcore.optisaludplus.modules.promoter.service.CommissionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * Read-only admin surface over the commissions ledger. Mutations
 * (mark-as-paid / void / dispute) live in their own bullets once the
 * payout flow lands.
 *
 * <p>Default sort {@code earnedAt DESC} matches the V26 composite index
 * {@code (member_id, earned_at DESC)} for the per-member view; the
 * canonical liquidation query {@code ?filter=promoter.uuid==X;status==PENDING}
 * is backed by {@code idx_commissions_promoter_status_period}.</p>
 */
@RestController
@RequestMapping("/v1/admin/commissions")
@RequiredArgsConstructor
public class AdminCommissionController {

    private final CommissionsService commissionsService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMMISSION_VIEW_ALL')")
    public ResponseEntity<Page<CommissionDto>> list(
            @PageableDefault(size = 20, sort = "earnedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(commissionsService.list(pageable, filter, q));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMMISSION_VIEW_ALL')")
    public ResponseEntity<CommissionDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(commissionsService.get(uuid));
    }
}
