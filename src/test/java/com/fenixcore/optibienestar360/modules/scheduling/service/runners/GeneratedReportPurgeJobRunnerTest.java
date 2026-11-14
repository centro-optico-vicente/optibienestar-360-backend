package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.audit.GeneratedReportPurgeService;
import com.fenixcore.optibienestar360.core.audit.GeneratedReportPurgeService.PurgeResult;
import com.fenixcore.optibienestar360.modules.system.entity.SystemConfig;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratedReportPurgeJobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:30:00Z");

    @Mock private GeneratedReportPurgeService purgeService;
    @Mock private SystemConfigService systemConfigService;

    private GeneratedReportPurgeJobRunner runner(int retentionDays) {
        SystemConfig config = new SystemConfig();
        config.setGeneratedReportRetentionDays(retentionDays);
        when(systemConfigService.getSystemConfig()).thenReturn(config);
        return new GeneratedReportPurgeJobRunner(purgeService, systemConfigService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void purgesWhatIsOlderThanTheSystemRetention() {
        GeneratedReportPurgeJobRunner runner = runner(30);
        when(purgeService.purgeUploadedBefore(NOW.minus(Duration.ofDays(30)))).thenReturn(new PurgeResult(42, 0, true));

        var result = runner.run();

        assertThat(result.summary()).containsEntry("purged", 42).containsEntry("retentionDays", 30);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void reportsFailuresSoTheRunIsVisibleInTheJobsAdmin() {
        GeneratedReportPurgeJobRunner runner = runner(7);
        when(purgeService.purgeUploadedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(new PurgeResult(10, 3, true));

        var result = runner.run();

        assertThat(result.errorMessage()).contains("3 file(s)");
        assertThat(result.summary()).containsEntry("failed", 3);
    }

    @Test
    void failsWhenStorageIsDisabled() {
        GeneratedReportPurgeJobRunner runner = runner(30);
        when(purgeService.purgeUploadedBefore(NOW.minus(Duration.ofDays(30)))).thenReturn(new PurgeResult(0, 0, false));

        assertThat(runner.run().errorMessage()).contains("disabled");
    }
}
