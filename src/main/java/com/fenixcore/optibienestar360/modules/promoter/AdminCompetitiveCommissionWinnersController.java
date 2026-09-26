package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveDecisionRevertRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveManualDecisionDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveManualDecisionRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveTieDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveTieResolveRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTie;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionManualDecisionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionTieRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CompetitiveCommissionEvaluationService.EvaluationOutcome;
import com.fenixcore.optibienestar360.modules.promoter.service.CompetitiveWinnerDecisionService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
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

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * D16 (hub plan competitive-commission-rules, Fase 2c) — the winners board: open ties needing a
 * coordinator's call, and the redirect/disqualify/revert actions over an evaluated period. Every
 * mutating action defaults {@code dryRun=true} so the frontend can preview the cascade before
 * confirming.
 */
@RestController
@RequestMapping("/v1/admin/competitive-commission-winners")
@RequiredArgsConstructor
public class AdminCompetitiveCommissionWinnersController {

    private final CompetitiveCommissionTieRepository tieRepository;
    private final CompetitiveCommissionManualDecisionRepository decisionRepository;
    private final CompetitiveWinnerDecisionService winnerDecisionService;

    @GetMapping("/ties")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL')")
    public ResponseEntity<Page<CompetitiveTieDto>> listTies(@PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter) {
        Specification<CompetitiveCommissionTie> spec = (root, query, cb) -> cb.conjunction();
        if (filter != null && !filter.isBlank()) {
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        return ResponseEntity.ok(tieRepository.findAll(spec, pageable).map(CompetitiveTieDto::from));
    }

    @GetMapping("/ties/{uuid}")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL')")
    public ResponseEntity<CompetitiveTieDto> getTie(@PathVariable UUID uuid) {
        return ResponseEntity.ok(CompetitiveTieDto.from(tieRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_tie.not_found"))));
    }

    /** The card's "historial de decisiones" for one rule + period, most recent first. */
    @GetMapping("/{ruleUuid}/periods/{periodStart}/decisions")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL')")
    public ResponseEntity<List<CompetitiveManualDecisionDto>> listDecisions(@PathVariable UUID ruleUuid,
            @PathVariable LocalDate periodStart) {
        return ResponseEntity.ok(decisionRepository.findByRule_UuidAndPeriodStartOrderByDecidedAtDesc(ruleUuid, periodStart)
                .stream().map(CompetitiveManualDecisionDto::from).toList());
    }

    @PostMapping("/ties/{uuid}/resolve")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_WINNER_DECIDE')")
    public ResponseEntity<EvaluationOutcome> resolveTie(@PathVariable UUID uuid,
            @Valid @RequestBody CompetitiveTieResolveRequest request,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(winnerDecisionService.resolveTie(
                uuid, request.winnerPromoterUuids(), request.reason(), actor.getUuid(), dryRun));
    }

    @PostMapping("/{ruleUuid}/periods/{periodStart}/decisions")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_WINNER_DECIDE')")
    public ResponseEntity<EvaluationOutcome> decide(@PathVariable UUID ruleUuid, @PathVariable LocalDate periodStart,
            @Valid @RequestBody CompetitiveManualDecisionRequest request,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(winnerDecisionService.decide(ruleUuid, periodStart, request.kind(),
                request.awardPosition(), request.promoterUuid(), request.replacementPromoterUuid(),
                request.excludeFromGroup(), request.reasonCategory(), request.reason(), actor.getUuid(), dryRun));
    }

    @PostMapping("/decisions/{uuid}/revert")
    @PreAuthorize("hasAuthority('COMPETITIVE_COMMISSION_WINNER_DECIDE')")
    public ResponseEntity<EvaluationOutcome> revertDecision(@PathVariable UUID uuid,
            @Valid @RequestBody CompetitiveDecisionRevertRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(winnerDecisionService.revert(uuid, request.reason(), actor.getUuid()));
    }
}
