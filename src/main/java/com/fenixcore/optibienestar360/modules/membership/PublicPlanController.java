package com.fenixcore.optibienestar360.modules.membership;

import com.fenixcore.optibienestar360.modules.membership.dto.PublicPlanDto;
import com.fenixcore.optibienestar360.modules.membership.service.PlansService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Anonymous-facing plan catalog for the public landing / pricing page. Mounted
 * under {@code /v1/public/**} which the security config
 * ({@code SecurityConfig.PUBLIC_PATHS}) already routes through
 * {@code permitAll()}, so no JWT is required.
 *
 * <p>Only <b>published + active</b> plans are ever returned, projected through
 * the sanitized {@link PublicPlanDto} — audit, status and publishing internals
 * are intentionally omitted; see that DTO's javadoc. Default order is monthly
 * fee ascending (cheapest first), the natural order for a pricing table. Mirrors
 * the {@code PublicAllyController} contract for consistency across the public
 * surface.</p>
 */
@RestController
@RequestMapping("/v1/public/plans")
@RequiredArgsConstructor
public class PublicPlanController {

    private final PlansService plansService;

    @GetMapping
    public ResponseEntity<Page<PublicPlanDto>> list(
            @PageableDefault(size = 50, sort = "monthlyFee", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(plansService.publicList(pageable));
    }

    /**
     * Detail for a single plan. 404s when the plan doesn't exist OR is not
     * publicly visible (inactive / unpublished) — anonymous callers never learn
     * an unpublished plan exists.
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<PublicPlanDto> detail(@PathVariable UUID uuid) {
        return ResponseEntity.ok(plansService.publicGetByUuid(uuid));
    }
}
