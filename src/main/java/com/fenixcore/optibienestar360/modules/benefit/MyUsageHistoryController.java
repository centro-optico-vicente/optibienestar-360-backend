package com.fenixcore.optibienestar360.modules.benefit;

import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageRatingRequest;
import com.fenixcore.optibienestar360.modules.benefit.dto.UsagePersonSummaryDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.UsageServiceSummaryDto;
import com.fenixcore.optibienestar360.modules.benefit.service.BenefitUsageRatingsService;
import com.fenixcore.optibienestar360.modules.benefit.service.BenefitUsagesService;
import com.fenixcore.optibienestar360.modules.benefit.service.BenefitUsagesService.UsageFilter;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service benefit-usage history ({@code GET /v1/me/usage-history},
 * vertical-9) — the affiliate's own consumption feed across every ally. Gated
 * by {@code MEMBER_VIEW_OWN}; scope is the caller's own membership history via
 * the repository query, newest usage first.
 *
 * <p>{@code PUT /{uuid}/rating} lets the affiliate rate one of those usages
 * (V201, {@code MEMBER_RATE_USAGE}); someone else's usage answers 404.</p>
 */
@RestController
@RequestMapping("/v1/me/usage-history")
@RequiredArgsConstructor
public class MyUsageHistoryController {

    private final BenefitUsagesService benefitUsagesService;
    private final BenefitUsageRatingsService ratingsService;

    /**
     * Optional filters: {@code holder=true} (only the holder's own usages),
     * {@code beneficiaryUuid} (one beneficiary's), {@code productUuid} (one
     * service across allies), {@code productCategoryUuid}.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<Page<BenefitUsageDto>> list(
            @RequestParam(defaultValue = "false") boolean holder,
            @RequestParam(required = false) UUID beneficiaryUuid,
            @RequestParam(required = false) UUID productUuid,
            @RequestParam(required = false) UUID productCategoryUuid,
            @PageableDefault(size = 20, sort = "usageDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        UsageFilter filter = new UsageFilter(holder, beneficiaryUuid, productUuid, productCategoryUuid);
        return ResponseEntity.ok(benefitUsagesService.listForMemberUser(actor.getUuid(), filter, pageable));
    }

    /** Consumption grouped by service, optionally for the holder or one beneficiary only. */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<List<UsageServiceSummaryDto>> summary(
            @RequestParam(defaultValue = "false") boolean holder,
            @RequestParam(required = false) UUID beneficiaryUuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(benefitUsagesService.summarizeForMemberUser(actor.getUuid(), holder, beneficiaryUuid));
    }

    /** Consumption count per person: the holder (null beneficiaryUuid) and each beneficiary. */
    @GetMapping("/by-person")
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<List<UsagePersonSummaryDto>> byPerson(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(benefitUsagesService.summarizeByPersonForMemberUser(actor.getUuid()));
    }

    @PutMapping("/{uuid}/rating")
    @PreAuthorize("hasAuthority('MEMBER_RATE_USAGE')")
    public ResponseEntity<BenefitUsageDto> rate(@PathVariable UUID uuid,
                                                @Valid @RequestBody BenefitUsageRatingRequest request,
                                                @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(ratingsService.rate(actor.getUuid(), uuid, request));
    }
}
