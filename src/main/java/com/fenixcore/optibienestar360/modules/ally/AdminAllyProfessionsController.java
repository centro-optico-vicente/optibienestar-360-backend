package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/professions}.
 *
 * <p>Single-item add / remove against the {@code @ManyToMany} pivot
 * {@code ally_professions}. For bulk-replace operations, use the parent's
 * {@code PUT /v1/admin/allies/{uuid}} with {@code professionUuids}.</p>
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/professions")
@RequiredArgsConstructor
public class AdminAllyProfessionsController {

    private final AlliesService alliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<List<ProfessionDto>> list(@PathVariable UUID allyUuid, Pageable pageable) {
        return ResponseEntity.ok(alliesService.listProfessions(allyUuid, pageable));
    }

    @PostMapping("/{professionUuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<Void> add(@PathVariable UUID allyUuid,
                                    @PathVariable UUID professionUuid) {
        alliesService.addProfession(allyUuid, professionUuid);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{professionUuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<Void> remove(@PathVariable UUID allyUuid,
                                       @PathVariable UUID professionUuid) {
        alliesService.removeProfession(allyUuid, professionUuid);
        return ResponseEntity.noContent().build();
    }
}
