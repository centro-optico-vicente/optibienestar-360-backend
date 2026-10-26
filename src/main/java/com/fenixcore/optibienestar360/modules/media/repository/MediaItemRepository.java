package com.fenixcore.optibienestar360.modules.media.repository;

import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findByUuid(UUID uuid);

    /** An owner's active gallery in display order. */
    List<MediaItem> findByOwnerTableAndOwnerUuidAndActiveTrueOrderBySortOrderAscIdAsc(String ownerTable, UUID ownerUuid);

    /** Batch variant for lists / details — avoids one query per owner. */
    List<MediaItem> findByOwnerTableAndOwnerUuidInAndActiveTrueOrderBySortOrderAscIdAsc(
            String ownerTable, Collection<UUID> ownerUuids);

    Optional<MediaItem> findByOwnerTableAndOwnerUuidAndPrimaryTrueAndActiveTrue(String ownerTable, UUID ownerUuid);

    /** Every owner's current primary image — drives {@code MediaLogoUrlSyncRunner}. */
    List<MediaItem> findByPrimaryTrueAndActiveTrue();

    /** The gallery row behind a stored file — used when the R2 object is moved. */
    List<MediaItem> findByAttachedFile_Id(Long attachedFileId);
}
