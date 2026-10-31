package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.core.config.PublicHttpCacheConfig;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyLicenseDto;
import com.fenixcore.optibienestar360.modules.ally.service.AllyLicensesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /v1/public/allies/{uuid}/licenses} — the VERIFIED professional
 * licenses of a published ally's holder person (V213), body + number only.
 * 404 when the ally is not publicly visible, same as the public detail.
 */
@RestController
@RequestMapping("/v1/public/allies/{uuid}/licenses")
@RequiredArgsConstructor
public class PublicAllyLicensesController {

    private final AllyLicensesService licensesService;

    @GetMapping
    public ResponseEntity<List<PublicAllyLicenseDto>> list(@PathVariable UUID uuid) {
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(licensesService.publicList(uuid));
    }
}
