package com.fenixcore.optibienestar360.core.util;

import com.fenixcore.optibienestar360.modules.patientaccess.repository.AllyPatientLinkRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A missing date bound must reach the query as a concrete instant, never as null:
 * PostgreSQL cannot type a null Instant parameter ("could not determine data type of parameter").
 */
class InstantBoundsTest {

    private static final Instant DAY = Instant.parse("2026-11-01T04:00:00Z");

    @Test
    void nullBoundsBecomeOpenEnded() {
        assertThat(InstantBounds.from(null)).isEqualTo(InstantBounds.MIN);
        assertThat(InstantBounds.to(null)).isEqualTo(InstantBounds.MAX);
        assertThat(InstantBounds.from(DAY)).isEqualTo(DAY);
        assertThat(InstantBounds.to(DAY)).isEqualTo(DAY);
    }

    @Test
    void ownPaymentsNeverPassNullDates() {
        PaymentRepository repo = mock(PaymentRepository.class, CALLS_REAL_METHODS);
        UUID user = UUID.randomUUID();
        Pageable page = Pageable.ofSize(20);

        repo.findOwnByUserUuid(user, page);
        verify(repo).findOwnByUserUuidBetween(user, InstantBounds.MIN, InstantBounds.MAX, page);

        repo.findOwnByUserUuidAndSearch(user, "abc", DAY, null, page);
        verify(repo).findOwnByUserUuidAndSearchBetween(user, "abc", DAY, InstantBounds.MAX, page);
    }

    @Test
    void doctorPatientsNeverPassNullDates() {
        AllyPatientLinkRepository repo = mock(AllyPatientLinkRepository.class, CALLS_REAL_METHODS);
        Pageable page = Pageable.ofSize(20);

        repo.findActiveByDoctor(7L, "", page);
        verify(repo).findActiveByDoctorBetween(7L, "", InstantBounds.MIN, InstantBounds.MAX, page);
    }
}
