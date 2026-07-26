package com.fenixcore.optibienestar360.modules.subsidy;

import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyDto;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidiesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Self-service subsidy view ({@code GET /v1/me/subsidies}, V41) — the
 * JWT-authenticated titular sees their own active subsidies for transparency (no
 * edit). Gated by {@code SUBSIDY_VIEW_OWN}; the service scopes strictly to the
 * caller's member record. A non-affiliate 404s with {@code me.member.not_enrolled},
 * same contract as {@code GET /v1/me/family}.
 */
@RestController
@RequestMapping("/v1/me/subsidies")
@RequiredArgsConstructor
public class MySubsidiesController {

    private final SubsidiesService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SUBSIDY_VIEW_OWN')")
    public ResponseEntity<List<SubsidyDto>> getMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.listForUser(actor.getUuid()));
    }
}
