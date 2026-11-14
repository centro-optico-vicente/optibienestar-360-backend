package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.common.storage.LinkExpirySweepService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkExpirySweepJobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-11-13T15:05:00Z");

    @Mock private LinkExpirySweepService sweepService;

    @Test
    void marksBothLinkTablesAtTheSameInstantAndReportsTheCounts() {
        when(sweepService.expireDownloadLinks(NOW)).thenReturn(12);
        when(sweepService.expireReportShares(NOW)).thenReturn(3);

        var result = new LinkExpirySweepJobRunner(sweepService, Clock.fixed(NOW, ZoneOffset.UTC)).run();

        assertThat(result.summary()).containsEntry("downloadLinks", 12).containsEntry("reportShares", 3);
        assertThat(new LinkExpirySweepJobRunner(sweepService, Clock.systemUTC()).code()).isEqualTo("LINK_EXPIRY_SWEEP");
    }
}
