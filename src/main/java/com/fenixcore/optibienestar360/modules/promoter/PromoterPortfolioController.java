package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * An advisor registering a brand-new person straight into their own portfolio (V201 "Nuevo en mi
 * cartera"): the person is created and attributed to the caller, with no membership. A collection
 * can then be registered for them via {@code POST /v1/advisors/me/payments}.
 */
@RestController
@RequestMapping({"/v1/me/advisor/portfolio", "/v1/advisors/me/portfolio"})
@RequiredArgsConstructor
public class PromoterPortfolioController {

    private final MembersService membersService;
    private final PromoterRepository promoterRepository;

    /** Members this advisor can collect from: own portfolio plus unadvised members; needs 2+ characters. */
    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('COLLECTION_CREATE_DOWNLINE')")
    public ResponseEntity<java.util.List<com.fenixcore.optibienestar360.modules.member.dto.PortfolioCandidateDto>> candidates(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal CustomUserDetails actor) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actor.getUuid())
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        return ResponseEntity.ok(membersService.listPortfolioCandidates(promoter, q, limit));
    }

    @PostMapping("/members")
    @PreAuthorize("hasAuthority('PROMOTER_PORTFOLIO_CREATE')")
    public ResponseEntity<MemberDetailDto> register(@Valid @RequestBody MemberCreateRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails actor) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actor.getUuid())
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        return ResponseEntity.status(201).body(membersService.createInPortfolio(request, promoter));
    }
}
