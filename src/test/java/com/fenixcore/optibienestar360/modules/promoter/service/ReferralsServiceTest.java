package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.dto.MyReferralDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral.ReferralStatus;
import com.fenixcore.optibienestar360.modules.promoter.mapper.ReferralMapperImpl;
import com.fenixcore.optibienestar360.modules.promoter.repository.ReferralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReferralsService}. Uses the real
 * {@code ReferralMapperImpl} so the @Mapping source paths
 * ({@code referred.uuid}, {@code referred.person.fullName},
 * {@code rewardPayment.uuid}) are exercised end-to-end — null-safe
 * generation for PENDING rows is verified by an explicit case.
 */
@ExtendWith(MockitoExtension.class)
class ReferralsServiceTest {

    @Mock private ReferralRepository repository;

    private ReferralsService service;

    @BeforeEach
    void setup() {
        service = new ReferralsService(repository, new ReferralMapperImpl());
    }

    @Test
    void list_empty_returns_empty_page() {
        UUID userUuid = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findOwnByUserUuid(eq(userUuid), any(Pageable.class)))
                .thenReturn(Page.empty(pageable));

        Page<MyReferralDto> page = service.listForUser(userUuid, pageable);

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void list_delegates_to_repo_with_caller_uuid_and_pageable() {
        UUID userUuid = UUID.randomUUID();
        Pageable pageable = PageRequest.of(2, 50);
        when(repository.findOwnByUserUuid(eq(userUuid), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        service.listForUser(userUuid, pageable);

        verify(repository).findOwnByUserUuid(userUuid, pageable);
    }

    @Test
    void list_maps_REWARD_GRANTED_referral_with_all_snapshot_fields() {
        UUID userUuid = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        Member referrer = bareMember(10L);
        Member referred = memberWithPerson(20L, "Maria Lopez");
        Payment rewardPayment = payment(77L);
        Referral row = new Referral();
        row.setId(1L);
        row.setUuid(UUID.randomUUID());
        row.setReferrer(referrer);
        row.setReferred(referred);
        row.setReferralCode("ABC123");
        row.setEnrolledAt(Instant.parse("2026-01-15T12:00:00Z"));
        row.setRewardPct(new BigDecimal("10.00"));
        row.setRewardCurrency("USD");
        row.setRewardPayment(rewardPayment);
        row.setRewardGrantedAt(Instant.parse("2026-02-05T09:30:00Z"));
        row.setStatus(ReferralStatus.REWARD_GRANTED.name());
        setCreatedAt(row, Instant.parse("2026-01-10T08:00:00Z"));

        when(repository.findOwnByUserUuid(eq(userUuid), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        Page<MyReferralDto> page = service.listForUser(userUuid, pageable);

        assertThat(page.getContent()).hasSize(1);
        MyReferralDto dto = page.getContent().get(0);
        assertThat(dto.uuid()).isEqualTo(row.getUuid());
        assertThat(dto.status()).isEqualTo("REWARD_GRANTED");
        assertThat(dto.referralCode()).isEqualTo("ABC123");
        assertThat(dto.referredMemberUuid()).isEqualTo(referred.getUuid());
        assertThat(dto.referredMemberName()).isEqualTo("Maria Lopez");
        assertThat(dto.enrolledAt()).isEqualTo(Instant.parse("2026-01-15T12:00:00Z"));
        assertThat(dto.rewardPct()).isEqualByComparingTo("10.00");
        assertThat(dto.rewardCurrency()).isEqualTo("USD");
        assertThat(dto.rewardPaymentUuid()).isEqualTo(rewardPayment.getUuid());
        assertThat(dto.rewardGrantedAt()).isEqualTo(Instant.parse("2026-02-05T09:30:00Z"));
    }

    @Test
    void list_maps_PENDING_row_with_null_referred_and_null_reward_payment() {
        UUID userUuid = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        Referral pending = new Referral();
        pending.setId(2L);
        pending.setUuid(UUID.randomUUID());
        pending.setReferrer(bareMember(10L));
        // referred null, no enrolledAt, no reward, no rewardPayment
        pending.setReferralCode("PENDXX");
        pending.setStatus(ReferralStatus.PENDING_ENROLLMENT.name());

        when(repository.findOwnByUserUuid(eq(userUuid), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending), pageable, 1));

        MyReferralDto dto = service.listForUser(userUuid, pageable).getContent().get(0);

        assertThat(dto.status()).isEqualTo("PENDING_ENROLLMENT");
        assertThat(dto.referralCode()).isEqualTo("PENDXX");
        // Null-safe source paths: referred is null, MapStruct emits null.
        assertThat(dto.referredMemberUuid()).isNull();
        assertThat(dto.referredMemberName()).isNull();
        assertThat(dto.rewardPaymentUuid()).isNull();
        assertThat(dto.rewardGrantedAt()).isNull();
        assertThat(dto.enrolledAt()).isNull();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static Member bareMember(long id) {
        Member m = new Member();
        m.setId(id);
        m.setUuid(UUID.randomUUID());
        return m;
    }

    private static Member memberWithPerson(long id, String fullName) {
        Member m = bareMember(id);
        Person p = new Person();
        p.setId(id + 100);
        try {
            Field f = Person.class.getDeclaredField("fullName");
            f.setAccessible(true);
            f.set(p, fullName);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        m.setPerson(p);
        return m;
    }

    private static Payment payment(long id) {
        Payment p = new Payment();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        return p;
    }

    private static void setCreatedAt(Referral r, Instant when) {
        // createdAt is in BaseEntity and there's a setter through Lombok.
        r.setCreatedAt(when);
    }
}
