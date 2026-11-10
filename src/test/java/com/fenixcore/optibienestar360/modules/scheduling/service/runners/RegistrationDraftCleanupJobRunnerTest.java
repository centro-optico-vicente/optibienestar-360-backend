package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService.CleanupResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The abandoned-draft cleanup job: retention window, on/off switch and the run summary. */
@ExtendWith(MockitoExtension.class)
class RegistrationDraftCleanupJobRunnerTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    @Mock private RegistrationDraftService draftService;

    @Test
    void run_discardsDraftsOlderThanTheRetentionWindowAndReportsTheCounts() {
        when(draftService.purgeAbandoned(any())).thenReturn(new CleanupResult(3, 2, 0));

        JobRunResult result = new RegistrationDraftCleanupJobRunner(draftService, NOW, true, 30).run();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(draftService).purgeAbandoned(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("memberDrafts", 3).containsEntry("allyDrafts", 2)
                .containsEntry("failures", 0).containsEntry("retentionDays", 30);
    }

    @Test
    void run_doesNothingWhenSwitchedOff() {
        JobRunResult result = new RegistrationDraftCleanupJobRunner(draftService, NOW, false, 30).run();

        verify(draftService, never()).purgeAbandoned(any());
        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("skipped", true);
    }

    @Test
    void run_fallsBackToSevenDaysWhenTheRetentionIsNotPositive() {
        when(draftService.purgeAbandoned(any())).thenReturn(new CleanupResult(0, 0, 0));

        new RegistrationDraftCleanupJobRunner(draftService, NOW, true, 0).run();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(draftService).purgeAbandoned(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(Instant.parse("2026-11-02T12:00:00Z"));
    }

    @Test
    void run_reportsAFailureWhenSomeDraftCouldNotBeDiscarded() {
        when(draftService.purgeAbandoned(any())).thenReturn(new CleanupResult(1, 0, 2));

        JobRunResult result = new RegistrationDraftCleanupJobRunner(draftService, NOW, true, 30).run();

        assertThat(result.success()).isFalse();
        assertThat(result.summary()).containsEntry("failures", 2);
    }
}
