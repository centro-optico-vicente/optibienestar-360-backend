package com.fenixcore.optibienestar360.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional(readOnly = true)
public interface FileTypePolicyRepository extends JpaRepository<FileTypePolicy, Long> {

    Optional<FileTypePolicy> findByVisibility(FileVisibility visibility);
}
