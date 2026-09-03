package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusEvaluationService;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusRulesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Admin CRUD over the configurable bonus rules (v2 PDF #5) plus the manual
 * evaluation trigger. Granular per V79: {@code BONUS_RULE_VIEW_ALL} /
 * {@code _CREATE} / {@code _UPDATE} / {@code _DELETE}. {@code /evaluate} is a
 * preview that doesn't persist, gated by create-or-update since it's part of
 * building/adjusting a rule, not viewing already-persisted ones. The scheduled
 * runner performs the same evaluation monthly without a request.
 */
@RestController
@RequestMapping("/v1/admin/bonus-rules")
@RequiredArgsConstructor
public class AdminBonusRuleController {

    private final BonusRulesService bonusRulesService;
    private final BonusEvaluationService bonusEvaluationService;

    @GetMapping
    @PreAuthorize("hasAuthority('BONUS_RULE_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<BonusRuleDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID promoterTypeUuid,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Page<BonusRuleDto> page = bonusRulesService.list(pageable, filter, q, promoterTypeUuid, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, bonusRulesService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('BONUS_RULE_VIEW_ALL')")
    public ResponseEntity<BonusRuleDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(bonusRulesService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BONUS_RULE_CREATE')")
    public ResponseEntity<BonusRuleDto> create(@Valid @RequestBody BonusRuleRequest request) {
        BonusRuleDto created = bonusRulesService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('BONUS_RULE_UPDATE')")
    public ResponseEntity<BonusRuleDto> update(@PathVariable UUID uuid,
                                               @Valid @RequestBody BonusRuleRequest request) {
        return ResponseEntity.ok(bonusRulesService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('BONUS_RULE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        bonusRulesService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Runs the engine now against {@code asOf} (or today). {@code dryRun=true}
     * previews the awards that would be granted without persisting.
     */
    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyAuthority('BONUS_RULE_CREATE', 'BONUS_RULE_UPDATE')")
    public ResponseEntity<BonusEvaluationResponse> evaluate(
            @RequestBody(required = false) BonusEvaluationRequest request) {
        BonusEvaluationRequest req = request != null ? request : new BonusEvaluationRequest(null, false);
        return ResponseEntity.ok(bonusEvaluationService.evaluate(req.asOf(), req.dryRun()));
    }
}
