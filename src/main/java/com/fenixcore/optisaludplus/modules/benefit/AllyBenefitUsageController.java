package com.fenixcore.optisaludplus.modules.benefit;

import com.fenixcore.optisaludplus.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optisaludplus.modules.benefit.dto.BenefitUsageRegisterRequest;
import com.fenixcore.optisaludplus.modules.benefit.service.BenefitUsagesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Ally-side benefit-usage registration surface. This commit only ships
 * the POST entry point — the usage-history GET endpoint
 * ({@code GET /v1/ally/usage-history}) lands in a follow-up bullet.
 *
 * <p>Expected flow at the counter:</p>
 * <ol>
 *   <li>Operator scans / types the affiliate's document.</li>
 *   <li>Frontend calls
 *       {@code GET /v1/ally/validate/{document}} — the validator returns
 *       the membership UUID + plan + ACTIVE status.</li>
 *   <li>Operator confirms the consumed service and amounts, frontend
 *       POSTs here. The service rejects the row if the membership is no
 *       longer ACTIVE (stale validation, race with admin cancel).</li>
 * </ol>
 */
@RestController
@RequestMapping("/v1/ally/benefit-usage")
@RequiredArgsConstructor
public class AllyBenefitUsageController {

    private final BenefitUsagesService benefitUsagesService;

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_REGISTER_USAGE')")
    public ResponseEntity<BenefitUsageDto> register(@Valid @RequestBody BenefitUsageRegisterRequest request) {
        BenefitUsageDto created = benefitUsagesService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
