package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionDto;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lookup {@code GET /v1/admin/allies/{allyUuid}/applicable-terms} — the
 * current ALIADO contract templates this ally may sign (general templates
 * plus those scoped to the ally, one of its types or their segment, V183),
 * most specific first. Feeds the "standard agreement version" select of the
 * agreement form, so it is gated by the agreement write permissions rather
 * than {@code TERMS_VIEW_ALL}. Always 200, {@code []} when none applies.
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/applicable-terms")
@RequiredArgsConstructor
public class AdminAllyApplicableTermsController {

    private final TermsVersionService termsVersionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ALLY_AGREEMENT_CREATE', 'ALLY_AGREEMENT_UPDATE')")
    public ResponseEntity<List<TermsVersionDto>> list(@PathVariable UUID allyUuid) {
        return ResponseEntity.ok(termsVersionService.listApplicableToAlly(allyUuid));
    }
}
