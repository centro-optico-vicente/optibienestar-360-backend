package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AdvisorAllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Advisor portal: the allies the caller brought in ({@code allies.captured_by_promoter_id}),
 * read-only ({@code PROMOTER_ALLY_VIEW_OWN}). The advisor is resolved from the JWT; 404 when the
 * caller has no active advisor record. {@code /v1/me/advisor/allies} is the primary path,
 * {@code /v1/advisors/me/allies} the secondary one.
 */
@RestController
@RequestMapping({"/v1/me/advisor/allies", "/v1/advisors/me/allies"})
@RequiredArgsConstructor
public class MyAdvisorAlliesController {

    private final AlliesService alliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_ALLY_VIEW_OWN')")
    public ResponseEntity<Page<AdvisorAllyListItemDto>> mine(
            @AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(alliesService.listCapturedBy(actor.getUuid(), q, pageable));
    }
}
