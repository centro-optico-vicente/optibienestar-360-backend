package com.fenixcore.optibienestar360.common.storage;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDownloadLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    @Mock private FileDownloadLinkRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectProvider<StorageService> storageProvider;
    @Mock private StorageService storage;
    @Mock private PresignedUrlPolicy presignedUrlPolicy;

    private FileDownloadLinkService service() {
        return new FileDownloadLinkService(repository, userRepository, storageProvider, presignedUrlPolicy,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FileDownloadLink link(Instant expiresAt, Integer maxDownloads, int downloadCount) {
        FileDownloadLink link = new FileDownloadLink();
        link.setId(7L);
        link.setToken(UUID.randomUUID());
        link.setVisibility(FileVisibility.TEMPORARY);
        link.setResourceTable("payments");
        link.setResourceUuid(UUID.randomUUID());
        link.setFileKey("reports/x.pdf");
        link.setExpiresAt(expiresAt);
        link.setMaxDownloads(maxDownloads);
        link.setDownloadCount(downloadCount);
        return link;
    }

    @Test
    void grantsAValidLinkThroughTheAtomicClaim() {
        FileDownloadLink link = link(NOW.plus(Duration.ofDays(2)), null, 0);
        when(repository.findByToken(link.getToken())).thenReturn(Optional.of(link));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(repository.claimDownload(7L, NOW)).thenReturn(1);
        when(storage.getBucket()).thenReturn("private");
        when(storage.generatePresignedUrl(eq("private"), eq("reports/x.pdf"), any())).thenReturn("https://r2/x");

        assertThat(service().resolvePresignedUrl(link.getToken())).isEqualTo("https://r2/x");
    }

    @Test
    void rejectsAndMarksALinkPastItsValidityEvenIfStillActive() {
        // The sweep has not run yet: the status still says ACTIVE.
        FileDownloadLink link = link(NOW.minusSeconds(1), null, 0);
        when(repository.findByToken(link.getToken())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service().resolvePresignedUrl(link.getToken()))
                .isInstanceOf(IllegalStateException.class).hasMessage("file.download_link.expired");
        verify(repository).markExpired(7L, NOW);
        verify(repository, never()).claimDownload(anyLong(), any());
    }

    @Test
    void rejectsARevokedLinkWithoutTouchingIt() {
        FileDownloadLink link = link(NOW.plus(Duration.ofDays(2)), null, 0);
        link.revoke(NOW.minusSeconds(60));
        when(repository.findByToken(link.getToken())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service().resolvePresignedUrl(link.getToken()))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).markExpired(anyLong(), any());
        verify(repository, never()).claimDownload(anyLong(), any());
    }

    @Test
    void rejectsWhenAConcurrentHitTookTheLastDownload() {
        FileDownloadLink link = link(NOW.plus(Duration.ofDays(2)), 3, 2);
        when(repository.findByToken(link.getToken())).thenReturn(Optional.of(link));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(repository.claimDownload(7L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service().resolvePresignedUrl(link.getToken()))
                .isInstanceOf(IllegalStateException.class).hasMessage("file.download_link.expired");
    }

    @Test
    void doesNotSpendADownloadWhenStorageIsUnavailable() {
        FileDownloadLink link = link(NOW.plus(Duration.ofDays(2)), 1, 0);
        when(repository.findByToken(link.getToken())).thenReturn(Optional.of(link));
        when(storageProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service().resolvePresignedUrl(link.getToken()))
                .hasMessage("file.download_link.storage_unavailable");
        verify(repository, never()).claimDownload(anyLong(), any());
    }

    @Test
    void unknownTokenIsNotFound() {
        UUID token = UUID.randomUUID();
        when(repository.findByToken(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().resolvePresignedUrl(token)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void revokeAllForMarksStatusAndRevokedAtTogether() {
        FileDownloadLink link = link(NOW.plus(Duration.ofDays(2)), null, 0);
        when(repository.findByResourceTableAndResourceUuidAndFileKeyAndStatus(
                "payments", link.getResourceUuid(), "reports/x.pdf", DownloadLinkStatus.ACTIVE))
                .thenReturn(List.of(link));

        service().revokeAllFor("payments", link.getResourceUuid(), "reports/x.pdf");

        assertThat(link.getStatus()).isEqualTo(DownloadLinkStatus.REVOKED);
        assertThat(link.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void validityChecksStatusTimeAndCap() {
        assertThat(link(NOW.plusSeconds(1), null, 0).isValid(NOW)).isTrue();
        assertThat(link(NOW, null, 0).isValid(NOW)).isTrue();
        assertThat(link(NOW.minusSeconds(1), null, 0).isValid(NOW)).isFalse();
        assertThat(link(NOW.plusSeconds(60), 2, 2).isValid(NOW)).isFalse();
        FileDownloadLink expired = link(NOW.plusSeconds(60), null, 0);
        expired.setStatus(DownloadLinkStatus.EXPIRED);
        assertThat(expired.isValid(NOW)).isFalse();
    }
}
