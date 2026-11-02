package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHourDto;
import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHoursRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyOpeningHoursService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/opening-hours} (hub ADR
 * 0031 §7): the ally's whole week, read and replaced at once.
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/opening-hours")
@RequiredArgsConstructor
public class AdminAllyOpeningHoursController {

    private final AllyOpeningHoursService openingHoursService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<List<OpeningHourDto>> get(@PathVariable UUID allyUuid) {
        return ResponseEntity.ok(openingHoursService.get(allyUuid));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ALLY_OPENING_HOURS_UPDATE')")
    public ResponseEntity<List<OpeningHourDto>> replace(@PathVariable UUID allyUuid,
                                                        @Valid @RequestBody OpeningHoursRequest request) {
        return ResponseEntity.ok(openingHoursService.replace(allyUuid, request));
    }
}
