package com.fenixcore.optibienestar360.common.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V249 link status against real Postgres: the atomic download claim (cap →
 * EXHAUSTED in the same UPDATE), the use-time EXPIRED mark and the hourly sweep.
 * Same skip-if-unreachable pattern as {@code LoginAuditIT}.
 */
@SpringBootTest
@ActiveProfiles("dev")
class LinkStatusIT {

    @Autowired private FileDownloadLinkRepository repository;
    @Autowired private LinkExpirySweepService sweepService;

    private final List<Long> created = new ArrayList<>();

    @BeforeAll
    static void assumePostgresReachable() {
        String host = System.getenv().getOrDefault("DATABASE_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("DATABASE_PORT", "5432"));
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            reachable = true;
        } catch (IOException ex) {
            reachable = false;
        }
        Assumptions.assumeTrue(reachable, "No Postgres reachable at " + host + ":" + port + " — skipping link status validation");
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAllById(created);
    }

    private FileDownloadLink save(Instant expiresAt, Integer maxDownloads) {
        FileDownloadLink link = new FileDownloadLink();
        link.setToken(UUID.randomUUID());
        link.setVisibility(FileVisibility.TEMPORARY);
        link.setResourceTable("link_status_it");
        link.setResourceUuid(UUID.randomUUID());
        link.setFileKey("it/" + UUID.randomUUID() + ".pdf");
        link.setExpiresAt(expiresAt);
        link.setMaxDownloads(maxDownloads);
        link.setCreatedAt(Instant.now());
        FileDownloadLink saved = repository.save(link);
        created.add(saved.getId());
        return saved;
    }

    private DownloadLinkStatus statusOf(FileDownloadLink link) {
        return repository.findById(link.getId()).orElseThrow().getStatus();
    }

    @Test
    void theDownloadThatReachesTheCapMarksTheLinkExhausted() {
        FileDownloadLink link = save(Instant.now().plus(Duration.ofDays(1)), 2);
        Instant now = Instant.now();

        assertThat(repository.claimDownload(link.getId(), now)).isEqualTo(1);
        assertThat(statusOf(link)).isEqualTo(DownloadLinkStatus.ACTIVE);
        assertThat(repository.claimDownload(link.getId(), now)).isEqualTo(1);
        assertThat(statusOf(link)).isEqualTo(DownloadLinkStatus.EXHAUSTED);
        assertThat(repository.claimDownload(link.getId(), now)).isZero();
        assertThat(repository.findById(link.getId()).orElseThrow().getDownloadCount()).isEqualTo(2);
    }

    @Test
    void anExpiredLinkCannotBeClaimedAndIsMarkedOnUse() {
        FileDownloadLink link = save(Instant.now().minusSeconds(60), null);
        Instant now = Instant.now();

        assertThat(repository.claimDownload(link.getId(), now)).isZero();
        assertThat(repository.markExpired(link.getId(), now)).isEqualTo(1);
        assertThat(statusOf(link)).isEqualTo(DownloadLinkStatus.EXPIRED);
    }

    @Test
    void theSweepMarksOnlyActiveLinksPastTheirValidity() {
        FileDownloadLink past = save(Instant.now().minusSeconds(60), null);
        FileDownloadLink future = save(Instant.now().plus(Duration.ofDays(1)), null);

        assertThat(sweepService.expireDownloadLinks(Instant.now())).isGreaterThanOrEqualTo(1);

        assertThat(statusOf(past)).isEqualTo(DownloadLinkStatus.EXPIRED);
        assertThat(statusOf(future)).isEqualTo(DownloadLinkStatus.ACTIVE);
    }
}
