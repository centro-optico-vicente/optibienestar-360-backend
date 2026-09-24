package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService.BatchResult;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Basic success-path unit test for {@link MembershipChargeGenerationJobRunner} (V153). */
@ExtendWith(MockitoExtension.class)
class MembershipChargeGenerationJobRunnerTest {

    @Mock private ScheduledJobRepository jobRepository;
    @Mock private MembershipChargeService membershipChargeService;

    private MembershipChargeGenerationJobRunner sut() {
        return new MembershipChargeGenerationJobRunner(jobRepository, membershipChargeService);
    }

    @Test
    void run_delegatesToService_andReportsSummary() {
        ScheduledJob job = new ScheduledJob();
        job.setCode(MembershipChargeGenerationJobRunner.CODE);
        job.setTimezone("America/Caracas");
        when(jobRepository.findByCode(MembershipChargeGenerationJobRunner.CODE)).thenReturn(Optional.of(job));
        when(membershipChargeService.generateUpcomingCharges(any(LocalDate.class)))
                .thenReturn(new BatchResult(10, 4));

        JobRunResult result = sut().run();

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("scanned", 10).containsEntry("created", 4);
    }
}
