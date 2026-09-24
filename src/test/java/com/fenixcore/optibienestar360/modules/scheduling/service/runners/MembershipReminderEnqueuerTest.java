package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationEnqueueCommand;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationService;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MembershipReminderEnqueuer} — turns a membership into a
 * queue command, and skips titulares with no email.
 */
@ExtendWith(MockitoExtension.class)
class MembershipReminderEnqueuerTest {

    @Mock private NotificationService notificationService;
    @Mock private MessageSource messageSource;
    @Mock private NotificationChannelResolver channelResolver;

    private MembershipReminderEnqueuer sut() {
        return new MembershipReminderEnqueuer(notificationService, messageSource, channelResolver);
    }

    @Test
    void enqueue_withEmail_pushesCommandAndReturnsTrue() {
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Recordatorio");
        Membership membership = membership("ana@x.com", "es");

        boolean result = sut().enqueue(membership, "payment-reminder", "email.payment.reminder.subject");

        assertThat(result).isTrue();
        ArgumentCaptor<NotificationEnqueueCommand> captor =
                ArgumentCaptor.forClass(NotificationEnqueueCommand.class);
        verify(notificationService).enqueue(captor.capture());
        NotificationEnqueueCommand cmd = captor.getValue();
        assertThat(cmd.recipientEmail()).isEqualTo("ana@x.com");
        assertThat(cmd.templateCode()).isEqualTo("payment-reminder");
        assertThat(cmd.recipientLocale()).isEqualTo("es");
        assertThat(cmd.sourceEntityUuid()).isEqualTo(membership.getUuid());
        assertThat(cmd.templateVars()).containsKey("planName");
    }

    @Test
    void enqueue_noEmail_returnsFalseAndDoesNotEnqueue() {
        Membership membership = membership(null, "es");

        assertThat(sut().enqueue(membership, "payment-reminder", "k")).isFalse();
        verify(notificationService, never()).enqueue(any());
    }

    private Membership membership(String email, String locale) {
        Person person = new Person();
        person.setUuid(UUID.randomUUID());
        person.setEmail(email);
        person.setLocale(locale);

        Member member = new Member();
        member.setPerson(person);

        Plan plan = new Plan();
        plan.setName("Familiar");

        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setNextDueDate(LocalDate.of(2026, 8, 1));
        membership.setMonthlyFee(new BigDecimal("5.00"));
        membership.setGracePeriodDays(7);
        return membership;
    }
}
