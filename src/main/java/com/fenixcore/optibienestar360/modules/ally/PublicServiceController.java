package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.PublicServiceListItemDto;
import com.fenixcore.optibienestar360.modules.ally.service.PublicServicesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Anonymous-facing <b>cross-ally</b> service catalog — the "who offers X?"
 * search. Mounted under {@code /v1/public/**} which the security config
 * ({@code SecurityConfig.PUBLIC_PATHS}) already routes through
 * {@code permitAll()}, so no JWT is required.
 *
 * <p>Returns only offerings cleared for public view (service + parent ally both
 * active + published, service {@code reviewStatus=APPROVED}) as the sanitized
 * {@link PublicServiceListItemDto}. Filtering: by service category UUID, by the
 * ally's city UUID, by free-text {@code q} (accent-insensitive over service
 * {@code name + description}). All optional; combined with AND. Default order is
 * service name ascending.</p>
 */
@RestController
@RequestMapping("/v1/public/services")
@RequiredArgsConstructor
public class PublicServiceController {

    private final PublicServicesService publicServicesService;

    @GetMapping
    public ResponseEntity<Page<PublicServiceListItemDto>> search(
            @RequestParam(required = false) UUID categoryUuid,
            @RequestParam(required = false) UUID cityUuid,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(publicServicesService.search(categoryUuid, cityUuid, q, pageable));
    }
}
