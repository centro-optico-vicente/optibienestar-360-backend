package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleListItemDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.service.CompetitiveCommissionRulesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * Admin CRUD for competitive commission rules (hub plan
 * competitive-commission-rules, Fase 1) — FIRST_TO_REACH / RANKING by
 * position, as opposed to the 4 threshold-based rule tables. The leaderboard
 * and recalculate endpoints (needing the evaluation engine) land in Fase 2,
 * along with the awards controller and {@code …_AWARD_*}/{@code
 * …_WINNER_DECIDE} permissions.
 */
@RestController
@RequestMapping("/v1/admin/competitive-commission-rules")
@RequiredArgsConstructor
public class AdminCompetitiveCommissionRuleController {

    private final CompetitiveCommissionRulesService service;

    @GetMapping
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_RULE_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<CompetitiveRuleListItemDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID campaignUuid,
            @RequestParam(defaultValue = "false") boolean campaignLinked,
            @RequestParam(required = false) CompetitiveMetric metric,
            @RequestParam(required = false) CompetitionType competitionType,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Page<CompetitiveRuleListItemDto> page = service.list(
                pageable, filter, q, campaignUuid, campaignLinked, metric, competitionType, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_RULE_VIEW_ALL')")
    public ResponseEntity<CompetitiveRuleDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_RULE_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.getUsage(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_RULE_CREATE')")
    public ResponseEntity<CompetitiveRuleDto> create(@Valid @RequestBody CompetitiveRuleCreateRequest request) {
        CompetitiveRuleDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_RULE_UPDATE')")
    public ResponseEntity<CompetitiveRuleDto> update(@PathVariable UUID uuid,
            @Valid @RequestBody CompetitiveRuleUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_RULE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        service.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }
}
