package com.fenixcore.optibienestar360.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AttachedFileRepository extends JpaRepository<AttachedFile, Long> {

    Optional<AttachedFile> findByUuid(UUID uuid);

    List<AttachedFile> findByOwnerTableAndOwnerUuidAndActiveTrue(String ownerTable, UUID ownerUuid);

    List<AttachedFile> findByOwnerTableAndOwnerUuidAndCategoryAndActiveTrue(
            String ownerTable, UUID ownerUuid, String category);

    /** Rows of an owner whose R2 key still sits under another prefix — see {@code ProductImageKeyMigrationRunner}. */
    List<AttachedFile> findByOwnerTableAndFileKeyContaining(String ownerTable, String keyFragment);
}
