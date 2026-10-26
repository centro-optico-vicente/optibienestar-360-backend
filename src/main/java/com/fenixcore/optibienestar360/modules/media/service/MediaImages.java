package com.fenixcore.optibienestar360.modules.media.service;

import com.fenixcore.optibienestar360.modules.media.dto.PublicImageDto;
import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read side of the galleries for public pages (hub ADR 0027): the images of
 * many owners in one query, in display order, so a list or a detail never
 * costs one query per ally or offer.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaImages {

    private final MediaItemRepository repository;
    private final MediaUrlResolver urls;

    /** {@code ownerUuid → images} for the owners that have any (others are absent). */
    public Map<UUID, List<PublicImageDto>> forOwners(String ownerTable, Collection<UUID> ownerUuids) {
        Map<UUID, List<PublicImageDto>> result = new HashMap<>();
        if (ownerUuids == null || ownerUuids.isEmpty()) {
            return result;
        }
        for (MediaItem item : repository.findByOwnerTableAndOwnerUuidInAndActiveTrueOrderBySortOrderAscIdAsc(
                ownerTable, ownerUuids)) {
            String url = urls.url(item);
            if (url != null) {
                result.computeIfAbsent(item.getOwnerUuid(), k -> new java.util.ArrayList<>())
                        .add(new PublicImageDto(url, item.getCaption(), item.isPrimary()));
            }
        }
        return result;
    }

    public List<PublicImageDto> forOwner(String ownerTable, UUID ownerUuid) {
        return forOwners(ownerTable, List.of(ownerUuid)).getOrDefault(ownerUuid, List.of());
    }
}
