package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionLifecycleTest {

    @Mock private MembershipPromotionRepository repository;

    private MembershipPromotion ongoing(Kind kind) {
        Promotion promotion = new Promotion();
        promotion.setKind(kind);
        MembershipPromotion mp = new MembershipPromotion();
        mp.setPromotion(promotion);
        mp.setStatus("ACTIVE");
        when(repository.findOngoing(10L)).thenReturn(Optional.of(mp));
        return mp;
    }

    private static Membership membership() {
        Membership m = new Membership();
        m.setId(10L);
        return m;
    }

    @Test
    void acquisitionPromotion_isLostOnAnyDelay() {
        MembershipPromotion suspended = ongoing(Kind.ACQUISITION);
        new PromotionLifecycle(repository).onStatusChanged(membership(), LifecycleStatus.SUSPENDED);
        assertThat(suspended.getStatus()).isEqualTo("CANCELED");
        assertThat(suspended.getEndedReason()).isEqualTo("Atraso en el pago");

        MembershipPromotion expired = ongoing(Kind.ACQUISITION);
        new PromotionLifecycle(repository).onStatusChanged(membership(), LifecycleStatus.EXPIRED);
        assertThat(expired.getStatus()).isEqualTo("CANCELED");
    }

    @Test
    void recoveryPromotion_survivesTheDelay_butNotACancellation() {
        MembershipPromotion mp = ongoing(Kind.RECOVERY);
        new PromotionLifecycle(repository).onStatusChanged(membership(), LifecycleStatus.EXPIRED);
        assertThat(mp.getStatus()).isEqualTo("ACTIVE");

        new PromotionLifecycle(repository).onStatusChanged(membership(), LifecycleStatus.CANCELED);
        assertThat(mp.getStatus()).isEqualTo("CANCELED");
        assertThat(mp.getEndedReason()).isEqualTo("Membresía cancelada");
    }

    @Test
    void returningToActive_keepsThePromotion() {
        MembershipPromotion mp = ongoing(Kind.ACQUISITION);
        new PromotionLifecycle(repository).onStatusChanged(membership(), LifecycleStatus.ACTIVE);
        assertThat(mp.getStatus()).isEqualTo("ACTIVE");
    }
}
