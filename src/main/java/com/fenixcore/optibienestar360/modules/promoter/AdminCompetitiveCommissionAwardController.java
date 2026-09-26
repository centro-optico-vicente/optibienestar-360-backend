package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveAwardPayRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveAwardSettlementDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveAwardVoidRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.CompetitiveCommissionAwardsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin read + pay/void over {@link com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward}
 * (hub plan competitive-commission-rules, Fase 2b). Creation is never manual — awards come only
 * from {@code CompetitiveCommissionEvaluationService}.
 */
@RestController
@RequestMapping("/v1/admin/competitive-commission-awards")
@RequiredArgsConstructor
public class AdminCompetitiveCommissionAwardController {

    private final CompetitiveCommissionAwardsService service;

    @GetMapping
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL')")
    public ResponseEntity<Page<CompetitiveAwardDto>> list(@PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(service.list(pageable, filter));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL')")
    public ResponseEntity<CompetitiveAwardDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @GetMapping("/{uuid}/settlements")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL')")
    public ResponseEntity<List<CompetitiveAwardSettlementDto>> settlements(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.settlements(uuid));
    }

    @PutMapping("/settlements/{uuid}/pay")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_PAY')")
    public ResponseEntity<Void> paySettlement(@PathVariable UUID uuid, @Valid @RequestBody CompetitiveAwardPayRequest request) {
        service.paySettlement(uuid, request.payoutReference(), request.payoutPaymentUuid());
        return ResponseEntity.noContent().build();
    }

    /** Shortcut: pays every PENDING settlement of the award with the same reference/payment. */
    @PutMapping("/{uuid}/pay")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_PAY')")
    public ResponseEntity<Map<String, Integer>> pay(@PathVariable UUID uuid, @Valid @RequestBody CompetitiveAwardPayRequest request) {
        int paid = service.payAward(uuid, request.payoutReference(), request.payoutPaymentUuid());
        return ResponseEntity.ok(Map.of("settlementsPaid", paid));
    }

    @PostMapping("/{uuid}/void")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_VOID')")
    public ResponseEntity<Void> voidAward(@PathVariable UUID uuid, @Valid @RequestBody CompetitiveAwardVoidRequest request) {
        service.voidAward(uuid, request.reason());
        return ResponseEntity.noContent().build();
    }
}
