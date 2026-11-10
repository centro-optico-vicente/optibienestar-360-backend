package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
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

/** The abandoned-draft cleanup job: per-kind retention read from the onboarding policy, 0 = never, and the run summary. */
@ExtendWith(MockitoExtension.class)
class RegistrationDraftCleanupJobRunnerTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    @Mock private RegistrationDraftService draftService;
    @Mock private OnboardingPolicyService policyService;

    private RegistrationDraftCleanupJobRunner runner(int memberDays, int allyDays) {
        when(policyService.memberDraftRetentionDays()).thenReturn(memberDays);
        when(policyService.allyDraftRetentionDays()).thenReturn(allyDays);
        return new RegistrationDraftCleanupJobRunner(draftService, policyService, NOW);
    }

    @Test
    void run_usesTheRetentionOfEachKindFromThePolicyAndReportsTheCounts() {
        when(draftService.purgeAbandoned(any(), any())).thenReturn(new CleanupResult(3, 2, 0));

        JobRunResult result = runner(15, 30).run();

        ArgumentCaptor<Instant> memberCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> allyCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(draftService).purgeAbandoned(memberCutoff.capture(), allyCutoff.capture());
        assertThat(memberCutoff.getValue()).isEqualTo(Instant.parse("2026-10-25T12:00:00Z"));
        assertThat(allyCutoff.getValue()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("memberDrafts", 3).containsEntry("allyDrafts", 2)
                .containsEntry("failures", 0).containsEntry("memberRetentionDays", 15).containsEntry("allyRetentionDays", 30);
    }

    @Test
    void run_zeroDaysForBothKindsMeansNothingIsCleaned() {
        JobRunResult result = runner(0, 0).run();

        verify(draftService, never()).purgeAbandoned(any(), any());
        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("skipped", true);
    }

    @Test
    void run_zeroDaysForOneKindSkipsOnlyThatKind() {
        when(draftService.purgeAbandoned(any(), any())).thenReturn(new CleanupResult(0, 1, 0));

        runner(0, 30).run();

        ArgumentCaptor<Instant> allyCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(draftService).purgeAbandoned(org.mockito.ArgumentMatchers.isNull(), allyCutoff.capture());
        assertThat(allyCutoff.getValue()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
    }

    @Test
    void run_reportsAFailureWhenSomeDraftCouldNotBeDiscarded() {
        when(draftService.purgeAbandoned(any(), any())).thenReturn(new CleanupResult(1, 0, 2));

        JobRunResult result = runner(15, 30).run();

        assertThat(result.success()).isFalse();
        assertThat(result.summary()).containsEntry("failures", 2);
    }
}
