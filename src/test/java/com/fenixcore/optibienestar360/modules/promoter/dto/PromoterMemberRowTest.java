package com.fenixcore.optibienestar360.modules.promoter.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromoterMemberRowTest {

    private static PromoterMemberRow row(String status, LocalDate nextDue) {
        return new PromoterMemberRow(UUID.randomUUID(), "Ana", "12345678", "+58 412", "Plan Familiar",
                LocalDate.now().minusMonths(3), status, nextDue, null, new BigDecimal("10.00"));
    }

    @Test
    void active_notYetDue_isUpToDate() {
        PromoterMemberRow r = row("ACTIVE", LocalDate.now().plusDays(5));
        assertThat(r.paymentStatus()).isEqualTo("UP_TO_DATE");
        assertThat(r.daysOverdue()).isZero();
    }

    @Test
    void active_pastDueDate_isDue_withDaysOverdue() {
        PromoterMemberRow r = row("ACTIVE", LocalDate.now().minusDays(3));
        assertThat(r.paymentStatus()).isEqualTo("DUE");
        assertThat(r.daysOverdue()).isEqualTo(3);
    }

    @Test
    void suspendedOrExpired_isOverdue() {
        assertThat(row("SUSPENDED", LocalDate.now().minusDays(40)).paymentStatus()).isEqualTo("OVERDUE");
        assertThat(row("EXPIRED", null).paymentStatus()).isEqualTo("OVERDUE");
    }

    @Test
    void withoutMembership_isNoMembership() {
        PromoterMemberRow r = row(null, null);
        assertThat(r.paymentStatus()).isEqualTo("NO_MEMBERSHIP");
        assertThat(r.daysOverdue()).isNull();
    }
}
