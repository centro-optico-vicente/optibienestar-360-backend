package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionNoticeServiceTest {

    @Mock private MembershipPromotionRepository repository;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private MembershipPromotion ongoing(Kind kind, Integer cyclesRemaining, String email) {
        Person person = new Person();
        person.setFullName("Ana Rojas");
        person.setEmail(email);
        Member member = new Member();
        member.setPerson(person);
        Membership membership = new Membership();
        membership.setMember(member);
        membership.setNextDueDate(TODAY.plusDays(5));
        Promotion promotion = new Promotion();
        promotion.setName("Octubre");
        promotion.setKind(kind);
        promotion.setDiscountPct(new BigDecimal("20.00"));
        MembershipPromotion mp = new MembershipPromotion();
        mp.setMembership(membership);
        mp.setPromotion(promotion);
        mp.setCyclesRemaining(cyclesRemaining);
        return mp;
    }

    @Test
    @SuppressWarnings("unchecked")
    void remindsAcquisitionMembers_andFlagsTheLastDiscountedCycle() {
        when(repository.findOngoingWithPaymentDueOn(TODAY.plusDays(5)))
                .thenReturn(List.of(ongoing(Kind.ACQUISITION, 1, "ana@example.com")));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Asunto");

        int sent = new PromotionNoticeService(repository, emailService, messageSource).sendDueNotices(TODAY, 5);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplated(eq("ana@example.com"), eq("Asunto"), eq("promotion-notice"), any(), vars.capture());
        assertThat(vars.getValue()).containsEntry("keepDiscount", true).containsEntry("lastCycle", true)
                .containsEntry("discountPct", "20");
    }

    @Test
    void skipsRecoveryPromotionsThatAreNotEnding_andMembersWithoutEmail() {
        when(repository.findOngoingWithPaymentDueOn(TODAY.plusDays(5))).thenReturn(List.of(
                ongoing(Kind.RECOVERY, 3, "x@example.com"),
                ongoing(Kind.ACQUISITION, null, null)));
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Asunto");

        int sent = new PromotionNoticeService(repository, emailService, messageSource).sendDueNotices(TODAY, 5);

        assertThat(sent).isZero();
        verify(emailService, never()).sendTemplated(any(), any(), any(), any(), any());
    }
}
