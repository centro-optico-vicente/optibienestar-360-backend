package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.OperationalLogRetentionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalLogRetentionJobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-11-12T07:45:00Z");

    @Mock private ScheduledJobRepository jobRepository;
    @Mock private OperationalLogRetentionService retentionService;

    private OperationalLogRetentionJobRunner runner() {
        return new OperationalLogRetentionJobRunner(jobRepository, retentionService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void jobWith(Map<String, Object> parameters) {
        ScheduledJob job = new ScheduledJob();
        job.setParameters(new HashMap<>(parameters));
        when(jobRepository.findByCode(OperationalLogRetentionJobRunner.CODE)).thenReturn(Optional.of(job));
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    @Test
    void usesTheRetentionDaysConfiguredOnTheJob() {
        jobWith(Map.of("notificationsDays", 30, "jobRunsDays", "60", "sessionLogDays", 365, "downloadLinksDays", 14));
        when(retentionService.purgeNotifications(daysAgo(30))).thenReturn(120);
        when(retentionService.purgeJobRuns(daysAgo(60))).thenReturn(800);
        when(retentionService.purgeSessionLog(daysAgo(365))).thenReturn(5);
        when(retentionService.purgeDownloadLinks(daysAgo(14))).thenReturn(0);

        var result = runner().run();

        assertThat(result.summary()).containsEntry("notifications", 120).containsEntry("jobRuns", 800)
                .containsEntry("sessionLog", 5).containsEntry("downloadLinks", 0)
                .containsEntry("jobRunsDays", 60);
    }

    @Test
    void fallsBackToTheDefaultsWhenParametersAreMissing() {
        jobWith(Map.of());

        runner().run();

        verify(retentionService).purgeNotifications(daysAgo(90));
        verify(retentionService).purgeJobRuns(daysAgo(90));
        verify(retentionService).purgeSessionLog(daysAgo(180));
        verify(retentionService).purgeDownloadLinks(daysAgo(30));
    }

    @Test
    void neverPurgesAnythingYoungerThanTheMinimum() {
        // A typo (0 or a negative number) must not wipe recent data.
        jobWith(Map.of("notificationsDays", 0, "jobRunsDays", -5, "sessionLogDays", 1, "downloadLinksDays", 3));

        runner().run();

        Instant minimum = daysAgo(OperationalLogRetentionJobRunner.MIN_DAYS);
        verify(retentionService).purgeNotifications(minimum);
        verify(retentionService).purgeJobRuns(minimum);
        verify(retentionService).purgeSessionLog(minimum);
        verify(retentionService).purgeDownloadLinks(minimum);
    }
}
