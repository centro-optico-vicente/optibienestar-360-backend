package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyBulkAssignResultDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk reassignment of the advisor who brought allies in (ADR LOC-0016). A separate bean so each
 * change goes through the {@link AlliesService#assignAdvisor} proxy and keeps its audit entry, while
 * the whole batch stays one transaction (all or nothing).
 */
@Service
@RequiredArgsConstructor
public class AllyAdvisorBulkService {

    private final AlliesService alliesService;
    private final AllyRepository allyRepository;
    private final PromoterRepository promoterRepository;

    /**
     * Moves every ally in {@code allyUuids} to {@code promoterUuid} (active, non-system); allies
     * already on it are skipped. An unknown ally or advisor is a 404 and rolls the batch back.
     */
    @Transactional
    public AllyBulkAssignResultDto bulkAssign(List<UUID> allyUuids, UUID promoterUuid) {
        Promoter target = promoterRepository.findByUuid(promoterUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        if (!target.isActive()) {
            throw new IllegalArgumentException("promoter.inactive");
        }
        if (target.isSystem()) {
            throw new IllegalArgumentException("promoter.system_not_assignable");
        }
        Set<UUID> uuids = new LinkedHashSet<>(allyUuids);
        int skipped = 0;
        for (UUID uuid : uuids) {
            Ally ally = allyRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
            Promoter current = ally.getCapturedByPromoter();
            if (current != null && current.getId().equals(target.getId())) {
                skipped++;
                continue;
            }
            alliesService.assignAdvisor(uuid, promoterUuid);
        }
        return new AllyBulkAssignResultDto(uuids.size(), skipped);
    }
}
