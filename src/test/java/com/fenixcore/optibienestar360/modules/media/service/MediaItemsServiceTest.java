package com.fenixcore.optibienestar360.modules.media.service;

import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.AttachedFileService;
import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.common.storage.dto.PublishResultDto;
import com.fenixcore.optibienestar360.modules.media.MediaScope;
import com.fenixcore.optibienestar360.modules.media.MediaTarget;
import com.fenixcore.optibienestar360.modules.media.dto.MediaItemDto;
import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gallery rules (hub ADR 0027) against an in-memory repository: the first
 * image is the primary one, at most one primary, deleting the primary promotes
 * the next, the per-owner limit, https-only links, strict reordering, and the
 * owner's {@code logo_url} following the primary image after every change.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaItemsServiceTest {

    private static final String TABLE = "allies";

    @Mock private MediaItemRepository repository;
    @Mock private MediaOwner owner;
    @Mock private AttachedFileService attachedFileService;
    @Mock private AttachedFileRepository attachedFileRepository;

    private final List<MediaItem> store = new ArrayList<>();
    private final UUID ownerUuid = UUID.randomUUID();
    private final CustomUserDetails actor = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es", List.of());

    private MediaItemsService service;
    private MediaTarget target;
    /** What the owner's {@code logo_url} column currently holds. */
    private String logoUrl = "unset";

    @BeforeEach
    void setUp() {
        when(owner.key()).thenReturn("ALLY");
        when(owner.ownerTable()).thenReturn(TABLE);
        org.mockito.Mockito.doAnswer(i -> {
            logoUrl = i.getArgument(1);
            return null;
        }).when(owner).setLogoUrl(any(), any());

        when(repository.findByOwnerTableAndOwnerUuidAndActiveTrueOrderBySortOrderAscIdAsc(anyString(), any()))
                .thenAnswer(i -> store.stream().filter(MediaItem::isActive)
                        .sorted(Comparator.comparingInt(MediaItem::getSortOrder)).toList());
        when(repository.findByOwnerTableAndOwnerUuidAndPrimaryTrueAndActiveTrue(anyString(), any()))
                .thenAnswer(i -> store.stream().filter(m -> m.isPrimary() && m.isActive()).findFirst());
        when(repository.findByUuid(any())).thenAnswer(i ->
                store.stream().filter(m -> m.getUuid().equals(i.getArgument(0))).findFirst());
        when(repository.save(any(MediaItem.class))).thenAnswer(i -> {
            MediaItem item = i.getArgument(0);
            if (item.getUuid() == null) item.setUuid(UUID.randomUUID());
            if (!store.contains(item)) store.add(item);
            return item;
        });

        MediaUrlResolver urls = new MediaUrlResolver();
        ReflectionTestUtils.setField(urls, "publicBaseUrl", "https://cdn.example");
        service = new MediaItemsService(repository, new MediaOwnerRegistry(List.of(owner)), urls,
                attachedFileService, attachedFileRepository);
        target = new MediaTarget(owner, ownerUuid, MediaScope.ADMIN, null);
    }

    private MediaItemDto add(String url) {
        return service.addExternal(target, actor, url, null);
    }

    @Test
    void firstImage_becomesPrimary_andLogoUrlFollowsIt() {
        MediaItemDto first = add("https://img.example/a.jpg");

        assertThat(first.primary()).isTrue();
        assertThat(first.sortOrder()).isZero();
        assertThat(first.source()).isEqualTo("URL");
        assertThat(logoUrl).isEqualTo("https://img.example/a.jpg");
    }

    @Test
    void secondImage_goesLast_andKeepsTheOldPrimary() {
        add("https://img.example/a.jpg");
        MediaItemDto second = add("https://img.example/b.jpg");

        assertThat(second.primary()).isFalse();
        assertThat(second.sortOrder()).isEqualTo(1);
        assertThat(logoUrl).isEqualTo("https://img.example/a.jpg");
    }

    @Test
    void makingAnotherImagePrimary_unsetsTheOld_andMovesLogoUrl() {
        MediaItemDto first = add("https://img.example/a.jpg");
        MediaItemDto second = add("https://img.example/b.jpg");

        service.update(target, actor, second.uuid(), null, true);

        assertThat(store).filteredOn(MediaItem::isPrimary).hasSize(1)
                .first().extracting(MediaItem::getUuid).isEqualTo(second.uuid());
        assertThat(store.stream().filter(m -> m.getUuid().equals(first.uuid())).findFirst().orElseThrow().isPrimary()).isFalse();
        assertThat(logoUrl).isEqualTo("https://img.example/b.jpg");
        verify(repository).flush();   // old primary is unset before the new one is set
    }

    @Test
    void demotingThePrimary_isRefused() {
        MediaItemDto first = add("https://img.example/a.jpg");

        assertThatThrownBy(() -> service.update(target, actor, first.uuid(), null, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.primary.required");
    }

    @Test
    void deletingThePrimary_promotesTheNextByOrder() {
        MediaItemDto first = add("https://img.example/a.jpg");
        MediaItemDto second = add("https://img.example/b.jpg");
        add("https://img.example/c.jpg");

        service.delete(target, actor, first.uuid());

        assertThat(store).filteredOn(m -> m.isActive() && m.isPrimary()).hasSize(1)
                .first().extracting(MediaItem::getUuid).isEqualTo(second.uuid());
        assertThat(logoUrl).isEqualTo("https://img.example/b.jpg");
    }

    @Test
    void deletingTheLastImage_clearsLogoUrl() {
        MediaItemDto only = add("https://img.example/a.jpg");

        service.delete(target, actor, only.uuid());

        assertThat(logoUrl).isNull();
    }

    @Test
    void anOwnerCannotExceedTheLimit() {
        for (int i = 0; i < MediaItemsService.MAX_PER_OWNER; i++) {
            add("https://img.example/" + i + ".jpg");
        }

        assertThatThrownBy(() -> add("https://img.example/extra.jpg"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.limit_reached");
    }

    @Test
    void externalUrls_mustBeHttps() {
        assertThatThrownBy(() -> add("http://img.example/a.jpg"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.url.invalid");
        assertThatThrownBy(() -> add("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.url.invalid");
        assertThatThrownBy(() -> add("not a url"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.url.invalid");
    }

    @Test
    void reorder_rewritesSortOrder_andRequiresTheWholeGallery() {
        UUID a = add("https://img.example/a.jpg").uuid();
        UUID b = add("https://img.example/b.jpg").uuid();
        UUID c = add("https://img.example/c.jpg").uuid();

        List<MediaItemDto> reordered = service.reorder(target, actor, List.of(c, a, b));

        assertThat(reordered).extracting(MediaItemDto::uuid).containsExactly(c, a, b);
        assertThatThrownBy(() -> service.reorder(target, actor, List.of(a, b)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.reorder.mismatch");
        assertThatThrownBy(() -> service.reorder(target, actor, List.of(a, b, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.reorder.mismatch");
    }

    @Test
    void upload_rejectsNonImages_beforeTouchingStorage() {
        MockMultipartFile pdf = new MockMultipartFile("file", "contract.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.upload(target, actor, pdf, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("media.file.not_image");
        verify(attachedFileService, never()).upload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void upload_storesAndPublishesAtOnce_thenUsesThePublicKey() {
        UUID fileUuid = UUID.randomUUID();
        AttachedFileDto stored = mock(AttachedFileDto.class);
        when(stored.uuid()).thenReturn(fileUuid);
        when(attachedFileService.upload(any(), any(), any(), any(), any(), any())).thenReturn(stored);
        when(attachedFileService.publish(fileUuid, true, null, actor.getUuid()))
                .thenReturn(PublishResultDto.permanent("public/media_items/x/front.png"));
        when(attachedFileRepository.findByUuid(fileUuid)).thenReturn(Optional.of(new AttachedFile()));
        MockMultipartFile image = new MockMultipartFile("file", "front.png", "image/png", new byte[]{1, 2});

        MediaItemDto uploaded = service.upload(target, actor, image, "Fachada");

        assertThat(uploaded.source()).isEqualTo("UPLOAD");
        assertThat(uploaded.url()).isEqualTo("https://cdn.example/public/media_items/x/front.png");
        assertThat(uploaded.caption()).isEqualTo("Fachada");
        assertThat(uploaded.primary()).isTrue();
        assertThat(logoUrl).isEqualTo("https://cdn.example/public/media_items/x/front.png");
    }

    @Test
    void anImageOfAnotherGallery_is404() {
        MediaItem foreign = new MediaItem();
        foreign.setUuid(UUID.randomUUID());
        foreign.setOwnerTable(TABLE);
        foreign.setOwnerUuid(UUID.randomUUID());
        store.add(foreign);

        assertThatThrownBy(() -> service.delete(target, actor, foreign.getUuid()))
                .isInstanceOf(java.util.NoSuchElementException.class).hasMessage("media.not_found");
    }

    @Test
    void replacePrimaryExternal_updatesTheExternalPrimary_orAddsOne() {
        add("https://img.example/old.jpg");

        service.replacePrimaryExternal("ALLY", ownerUuid, "https://img.example/new.jpg");

        assertThat(store).hasSize(1);
        assertThat(logoUrl).isEqualTo("https://img.example/new.jpg");
    }
}
