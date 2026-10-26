package com.fenixcore.optibienestar360.modules.media.service;

import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.AttachedFileService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.modules.media.MediaAction;
import com.fenixcore.optibienestar360.modules.media.MediaTarget;
import com.fenixcore.optibienestar360.modules.media.dto.MediaItemDto;
import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The one piece of logic behind every gallery (hub ADR 0027): list, upload a
 * file, add an external link, edit, reorder and delete — for any registered
 * {@link MediaOwner}. Uploads are <b>published immediately</b> (no approval
 * step); who may do what is decided per owner ({@link MediaOwner#authorize}).
 *
 * <p>Invariants kept here, in one transaction per call:</p>
 * <ul>
 *   <li>the first image of an owner becomes its primary one;</li>
 *   <li>at most one active primary image per owner (the unique partial index is
 *       the backstop — the old primary is unset and flushed before the new one is set);</li>
 *   <li>deleting the primary promotes the next image by order;</li>
 *   <li>at most {@link #MAX_PER_OWNER} active images per owner;</li>
 *   <li>after every change the owner's {@code logo_url} is rewritten with the
 *       primary image's URL (or {@code null}), so lists never join this table.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaItemsService {

    public static final int MAX_PER_OWNER = 10;

    /**
     * Uploaded files hang off the media item itself ({@code owner_table =
     * 'media_items'}), not off the gallery owner: a gallery image never shows up
     * among an ally's or member's documents, and any future owner works unchanged.
     */
    public static final String FILE_OWNER_TABLE = "media_items";
    public static final String FILE_CATEGORY = "MEDIA";

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final MediaItemRepository repository;
    private final MediaOwnerRegistry owners;
    private final MediaUrlResolver urls;
    private final AttachedFileService attachedFileService;
    private final AttachedFileRepository attachedFileRepository;

    // ─── Read ────────────────────────────────────────────────────────────────

    public List<MediaItemDto> list(MediaTarget target, CustomUserDetails actor) {
        authorize(MediaAction.READ, target, actor);
        return active(target).stream().map(this::toDto).toList();
    }

    // ─── Write (authorized) ──────────────────────────────────────────────────

    /** Uploads an image file to R2, publishes it right away and appends it to the gallery. */
    @Transactional
    public MediaItemDto upload(MediaTarget target, CustomUserDetails actor, MultipartFile file, String caption) {
        authorize(MediaAction.UPLOAD, target, actor);
        requireImage(file);
        List<MediaItem> items = active(target);
        requireRoom(items);

        // The item uuid is fixed first: the stored file is owned by the item (FILE_OWNER_TABLE).
        UUID itemUuid = UUID.randomUUID();
        AttachedFileDto stored = attachedFileService.upload(FILE_OWNER_TABLE, itemUuid, FileVisibility.INTERNAL,
                FILE_CATEGORY, file, actor.getUuid());
        String publicKey = attachedFileService.publish(stored.uuid(), true, null, actor.getUuid()).publicKey();
        AttachedFile storedFile = attachedFileRepository.findByUuid(stored.uuid())
                .orElseThrow(() -> new NoSuchElementException("file.not_found"));

        MediaItem item = newItem(target, items, caption);
        item.setUuid(itemUuid);
        item.setAttachedFile(storedFile);
        item.setPublicKey(publicKey);
        return toDto(saveAndSync(target, item));
    }

    /** Adds an image by external link (https only). */
    @Transactional
    public MediaItemDto addExternal(MediaTarget target, CustomUserDetails actor, String url, String caption) {
        authorize(MediaAction.UPLOAD, target, actor);
        List<MediaItem> items = active(target);
        requireRoom(items);

        MediaItem item = newItem(target, items, caption);
        item.setExternalUrl(requireHttps(url));
        return toDto(saveAndSync(target, item));
    }

    /** Edits caption and / or makes the image the primary one. */
    @Transactional
    public MediaItemDto update(MediaTarget target, CustomUserDetails actor, UUID mediaUuid, String caption, Boolean primary) {
        authorize(MediaAction.UPDATE, target, actor);
        MediaItem item = find(target, mediaUuid);

        if (caption != null) {
            item.setCaption(blankToNull(caption));
        }
        if (Boolean.TRUE.equals(primary) && !item.isPrimary()) {
            for (MediaItem other : active(target)) {
                if (other.isPrimary()) {
                    other.setPrimary(false);
                }
            }
            repository.flush();   // unset before set: at most one primary at any instant
            item.setPrimary(true);
        } else if (Boolean.FALSE.equals(primary) && item.isPrimary()) {
            // A gallery with images always has a primary one: demoting means "make another one primary".
            throw new IllegalArgumentException("media.primary.required");
        }
        syncLogoUrl(target.owner(), target.ownerUuid());
        return toDto(item);
    }

    /** Rewrites {@code sort_order} so the gallery follows {@code orderedUuids} — which must list every active image once. */
    @Transactional
    public List<MediaItemDto> reorder(MediaTarget target, CustomUserDetails actor, List<UUID> orderedUuids) {
        authorize(MediaAction.UPDATE, target, actor);
        List<MediaItem> items = active(target);

        Set<UUID> expected = new HashSet<>();
        items.forEach(i -> expected.add(i.getUuid()));
        if (orderedUuids.size() != expected.size() || !expected.equals(new HashSet<>(orderedUuids))) {
            throw new IllegalArgumentException("media.reorder.mismatch");
        }
        for (int position = 0; position < orderedUuids.size(); position++) {
            UUID uuid = orderedUuids.get(position);
            int order = position;
            items.stream().filter(i -> i.getUuid().equals(uuid)).findFirst().ifPresent(i -> i.setSortOrder(order));
        }
        return active(target).stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(this::toDto)
                .toList();
    }

    /** Removes an image (and its stored file); if it was the primary one the next image takes over. */
    @Transactional
    public void delete(MediaTarget target, CustomUserDetails actor, UUID mediaUuid) {
        authorize(MediaAction.DELETE, target, actor);
        MediaItem item = find(target, mediaUuid);
        boolean wasPrimary = item.isPrimary();

        item.setActive(false);
        item.setPrimary(false);
        repository.flush();

        if (item.getAttachedFile() != null) {
            attachedFileService.delete(item.getAttachedFile().getUuid());
        }
        if (wasPrimary) {
            active(target).stream().findFirst().ifPresent(next -> next.setPrimary(true));
        }
        syncLogoUrl(target.owner(), target.ownerUuid());
    }

    // ─── Internal (no authorization; callers are trusted services) ───────────

    /**
     * Makes {@code url} the owner's primary image — used by the deprecated
     * {@code logoUrl} field of the ally API. An external primary image is
     * updated in place; otherwise a new external image is appended and promoted.
     */
    @Transactional
    public void replacePrimaryExternal(String ownerKey, UUID ownerUuid, String url) {
        MediaOwner owner = owners.byKey(ownerKey);
        String https = requireHttps(url);
        List<MediaItem> items = repository
                .findByOwnerTableAndOwnerUuidAndActiveTrueOrderBySortOrderAscIdAsc(owner.ownerTable(), ownerUuid);

        Optional<MediaItem> primary = items.stream().filter(MediaItem::isPrimary).findFirst();
        if (primary.isPresent() && !primary.get().isUploaded()) {
            primary.get().setExternalUrl(https);
        } else {
            requireRoom(items);
            MediaItem item = new MediaItem();
            item.setOwnerTable(owner.ownerTable());
            item.setOwnerUuid(ownerUuid);
            item.setExternalUrl(https);
            item.setSortOrder(items.stream().mapToInt(MediaItem::getSortOrder).max().orElse(-1) + 1);
            primary.ifPresent(p -> p.setPrimary(false));
            repository.flush();
            item.setPrimary(true);
            repository.save(item);
        }
        syncLogoUrl(owner, ownerUuid);
    }

    /** Rewrites an owner's {@code logo_url} from its current primary image. */
    @Transactional
    public void syncLogoUrl(MediaOwner owner, UUID ownerUuid) {
        String url = repository
                .findByOwnerTableAndOwnerUuidAndPrimaryTrueAndActiveTrue(owner.ownerTable(), ownerUuid)
                .map(urls::url)
                .orElse(null);
        owner.setLogoUrl(ownerUuid, url);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void authorize(MediaAction action, MediaTarget target, CustomUserDetails actor) {
        target.owner().requireExists(target.ownerUuid(), target.parentUuid());
        target.owner().authorize(action, target, actor);
    }

    private List<MediaItem> active(MediaTarget target) {
        return repository.findByOwnerTableAndOwnerUuidAndActiveTrueOrderBySortOrderAscIdAsc(
                target.owner().ownerTable(), target.ownerUuid());
    }

    private MediaItem find(MediaTarget target, UUID mediaUuid) {
        MediaItem item = repository.findByUuid(mediaUuid)
                .filter(MediaItem::isActive)
                .orElseThrow(() -> new NoSuchElementException("media.not_found"));
        // The image must belong to the gallery named in the URL.
        if (!item.getOwnerTable().equals(target.owner().ownerTable()) || !item.getOwnerUuid().equals(target.ownerUuid())) {
            throw new NoSuchElementException("media.not_found");
        }
        return item;
    }

    private MediaItem newItem(MediaTarget target, List<MediaItem> items, String caption) {
        MediaItem item = new MediaItem();
        item.setOwnerTable(target.owner().ownerTable());
        item.setOwnerUuid(target.ownerUuid());
        item.setCaption(blankToNull(caption));
        item.setSortOrder(items.stream().mapToInt(MediaItem::getSortOrder).max().orElse(-1) + 1);
        item.setPrimary(items.isEmpty());   // the first image is the primary one
        return item;
    }

    private MediaItem saveAndSync(MediaTarget target, MediaItem item) {
        MediaItem saved = repository.save(item);
        syncLogoUrl(target.owner(), target.ownerUuid());
        return saved;
    }

    private MediaItemDto toDto(MediaItem item) {
        return new MediaItemDto(
                item.getUuid(),
                urls.url(item),
                item.isUploaded() ? "UPLOAD" : "URL",
                item.getCaption(),
                item.getSortOrder(),
                item.isPrimary(),
                item.getAttachedFile() != null ? item.getAttachedFile().getFileName() : null,
                item.getCreatedAt());
    }

    private static void requireRoom(List<MediaItem> items) {
        if (items.size() >= MAX_PER_OWNER) {
            throw new IllegalArgumentException("media.limit_reached");
        }
    }

    private static void requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("media.file.required");
        }
        String name = file.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        if (!IMAGE_EXTENSIONS.contains(extension) || contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("media.file.not_image");
        }
    }

    /** Only well-formed https links with a host: they are rendered as {@code <img src>} on public pages. */
    static String requireHttps(String url) {
        String candidate = url == null ? "" : url.trim();
        try {
            URI uri = new URI(candidate);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || candidate.length() > 500) {
                throw new IllegalArgumentException("media.url.invalid");
            }
            return candidate;
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("media.url.invalid");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
