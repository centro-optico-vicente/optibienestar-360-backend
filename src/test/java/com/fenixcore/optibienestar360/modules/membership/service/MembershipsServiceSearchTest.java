package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipSearchCriteria;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipsServiceSearchTest {

    @Mock private MemberRepository memberRepository;
    @Mock private PlanRepository planRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipMapper mapper;
    @Mock private ConversionEnricher conversionEnricher;
    @Mock private com.fenixcore.optibienestar360.modules.promotion.service.PromotionAssignmentService promotionAssignmentService;

    private MembershipsService sut() {
        return new MembershipsService(memberRepository, planRepository, membershipRepository, mapper,
                conversionEnricher, promotionAssignmentService, null,
                org.mockito.Mockito.mock(InscriptionPaymentMethodResolver.class));
    }

    private static MembershipSearchCriteria byStatus(String status) {
        return new MembershipSearchCriteria(null, null, null, null, null, status, null, null, null, null, null);
    }

    private static Membership membership(String status, LocalDate nextDue) {
        Person person = new Person();
        person.setFirstName("Ana");
        person.setLastName("Perez");
        Member member = new Member();
        member.setUuid(UUID.randomUUID());
        member.setPerson(person);
        Plan plan = new Plan();
        plan.setUuid(UUID.randomUUID());
        plan.setCode("IND");
        plan.setName("Individual");
        Currency currency = new Currency();
        currency.setCode("USD");
        Membership m = new Membership();
        m.setUuid(UUID.randomUUID());
        m.setMember(member);
        m.setPlan(plan);
        m.setCurrency(currency);
        m.setStatus(status);
        m.setNextDueDate(nextDue);
        m.setMonthlyFee(new BigDecimal("10.00"));
        return m;
    }

    @Test
    void search_rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> sut().search(byStatus("BOGUS"), PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membership.search.status_invalid");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_flagsAnActiveMembershipPastItsDueDateAsOverdue() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Caracas"));
        Membership late = membership("ACTIVE", today.minusDays(3));
        when(membershipRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(late)));

        var row = sut().search(byStatus("OVERDUE"), PageRequest.of(0, 20)).getContent().get(0);

        assertThat(row.paymentStatus()).isEqualTo("OVERDUE");
        assertThat(row.daysOverdue()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_hasNoPaymentStatusForACanceledMembership() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Caracas"));
        Membership canceled = membership("CANCELED", today.minusDays(40));
        when(membershipRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(canceled)));

        var row = sut().search(byStatus("CANCELED"), PageRequest.of(0, 20)).getContent().get(0);

        assertThat(row.paymentStatus()).isNull();
        assertThat(row.daysOverdue()).isZero();
    }
}
