package com.fenixcore.optibienestar360.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface FileDownloadLinkRepository extends JpaRepository<FileDownloadLink, Long> {

    Optional<FileDownloadLink> findByToken(UUID token);

    List<FileDownloadLink> findByResourceTableAndResourceUuidAndFileKeyAndRevokedAtIsNull(
            String resourceTable, UUID resourceUuid, String fileKey);
}
