package com.fenixcore.optibienestar360.modules.member.repository;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the "confirmed enrollment" definition behind
 * {@link MemberRepository#countNewSubscribersForPromoter} (hub ADR 0013, "Confirmados").
 *
 * <p>The suite runs on H2 with Flyway disabled (no schema), so the JPQL itself cannot be executed
 * here; instead the query text — the single named predicate — and the LocalDate-window to
 * Instant-range conversion are asserted. Pending (no approved payment: {@code confirmed_at} NULL),
 * rejected (a rejected payment never stamps it) and cancelled/deactivated ({@code active=false})
 * enrollments are excluded by that predicate; the stamping side is covered in
 * {@code PaymentsServiceTest} (first approved payment confirms, reject/non-first/already-confirmed
 * do not) and {@code MemberConfirmationServiceTest} (manual confirmation).</p>
 */
class MemberRepositoryConfirmedCountTest {

    private static String confirmedCountQuery() throws NoSuchMethodException {
        Method m = MemberRepository.class.getMethod("countConfirmedSubscribersForPromoterBetween",
                Long.class, Instant.class, Instant.class);
        return m.getAnnotation(Query.class).value();
    }

    @Test
    void predicate_requiresActiveAndConfirmed_soPendingRejectedAndCancelledAreExcluded() {
        assertThat(MemberRepository.CONFIRMED_ENROLLMENT_PREDICATE)
                .contains("m.active = true")
                .contains("m.confirmedAt IS NOT NULL");
    }

    @Test
    void query_usesThePredicate_andFiltersByConfirmedAtNotEnrolledAt() throws Exception {
        String jpql = confirmedCountQuery();

        assertThat(jpql).contains(MemberRepository.CONFIRMED_ENROLLMENT_PREDICATE);
        assertThat(jpql).contains("m.confirmedAt >= :from").contains("m.confirmedAt < :to");
        assertThat(jpql).doesNotContain("enrolledAt");
    }

    @Test
    void countNewSubscribersForPromoter_convertsInclusiveLocalDatesToHalfOpenInstantRange() {
        MemberRepository repository = mock(MemberRepository.class, CALLS_REAL_METHODS);
        when(repository.countConfirmedSubscribersForPromoterBetween(eq(7L), any(), any())).thenReturn(42L);

        long count = repository.countNewSubscribersForPromoter(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(count).isEqualTo(42L);
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countConfirmedSubscribersForPromoterBetween(eq(7L), from.capture(), to.capture());
        // First instant of June 1st through the first instant of July 1st (exclusive), in the app zone.
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay(AppTimeZone.ZONE).toInstant());
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 7, 1).atStartOfDay(AppTimeZone.ZONE).toInstant());
    }

    @Test
    void countNewSubscribersForPromoter_lastDayOfWindowIsInclusive() {
        MemberRepository repository = mock(MemberRepository.class, CALLS_REAL_METHODS);
        LocalDate day = LocalDate.of(2026, 6, 15);

        repository.countNewSubscribersForPromoter(7L, day, day);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countConfirmedSubscribersForPromoterBetween(eq(7L), from.capture(), to.capture());
        assertThat(java.time.Duration.between(from.getValue(), to.getValue())).isEqualTo(java.time.Duration.ofHours(24));
    }
}
