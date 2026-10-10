package com.fenixcore.optibienestar360.modules.person.service;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.AttachedFileService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Self-service profile photo of the authenticated user's Person (afiliado,
 * promotor, or any other role). Stored as a PUBLIC {@code attached_files} row
 * owned by {@code persons/<personUuid>} under {@link #CATEGORY} — no column on
 * {@code persons}: the newest active row is the current photo, and uploading a
 * new one deletes the previous (replace semantics, like ally service images).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfilePhotoService {

    public static final String OWNER_TABLE = "persons";
    public static final String CATEGORY = "PROFILE_PHOTO";

    private final UserRepository userRepository;
    private final AttachedFileRepository attachedFileRepository;
    private final AttachedFileService attachedFileService;
    private final ObjectProvider<StorageService> storageProvider;

    /** Public URL of the current photo, or {@code null} when none was uploaded. */
    public String currentUrl(UUID userUuid) {
        Person person = personOf(userUuid);
        return attachedFileRepository
                .findByOwnerTableAndOwnerUuidAndCategoryAndActiveTrue(OWNER_TABLE, person.getUuid(), CATEGORY)
                .stream()
                .filter(f -> f.getVisibility() == FileVisibility.PUBLIC)
                .max(Comparator.comparing(AttachedFile::getUploadedAt))
                .map(f -> publicUrl(f.getFileKey()))
                .orElse(null);
    }

    @Transactional
    public String replace(UUID userUuid, MultipartFile image) {
        Person person = personOf(userUuid);
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("profile.photo.not_an_image");
        }
        deleteExisting(person.getUuid());
        attachedFileService.upload(OWNER_TABLE, person.getUuid(), FileVisibility.PUBLIC, CATEGORY, image, userUuid);
        return currentUrl(userUuid);
    }

    @Transactional
    public void delete(UUID userUuid) {
        deleteExisting(personOf(userUuid).getUuid());
    }

    private void deleteExisting(UUID personUuid) {
        attachedFileRepository
                .findByOwnerTableAndOwnerUuidAndCategoryAndActiveTrue(OWNER_TABLE, personUuid, CATEGORY)
                .forEach(f -> attachedFileService.delete(f.getUuid()));
    }

    private Person personOf(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        if (user.getPerson() == null) throw new NoSuchElementException("profile.photo.no_person");
        return user.getPerson();
    }

    private String publicUrl(String key) {
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) return null;
        String base = storage.getPublicBaseUrl();
        return base.endsWith("/") ? base + key : base + "/" + key;
    }
}
