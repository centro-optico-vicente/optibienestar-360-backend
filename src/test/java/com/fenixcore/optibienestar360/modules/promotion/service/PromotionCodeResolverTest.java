package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionCodeResolverTest {

    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private AllyRepository allyRepository;

    private PromotionCodeResolver resolver() {
        return new PromotionCodeResolver(promoterRepository, memberRepository, allyRepository);
    }

    @Test
    void resolvesAPromoterCode_firstAndCaseInsensitively() {
        Promoter promoter = new Promoter();
        promoter.setActive(true);
        when(promoterRepository.findByReferralCode("VICENTE")).thenReturn(Optional.of(promoter));

        var owner = resolver().resolve(" vicente ").orElseThrow();

        assertThat(owner.code()).isEqualTo("VICENTE");
        assertThat(owner.promoter()).isSameAs(promoter);
    }

    @Test
    void fallsBackToMember_thenAlly_andSkipsInactiveOwners() {
        Promoter inactive = new Promoter();
        inactive.setActive(false);
        when(promoterRepository.findByReferralCode("AMIGO")).thenReturn(Optional.of(inactive));
        when(memberRepository.findByReferralCode("AMIGO")).thenReturn(Optional.empty());
        Ally ally = new Ally();
        when(allyRepository.findByReferralCode("AMIGO")).thenReturn(Optional.of(ally));

        var owner = resolver().resolve("AMIGO").orElseThrow();

        assertThat(owner.ally()).isSameAs(ally);
        assertThat(owner.promoter()).isNull();
    }

    @Test
    void resolvesAReferringMember() {
        Member member = new Member();
        when(promoterRepository.findByReferralCode("JUAN-01")).thenReturn(Optional.empty());
        when(memberRepository.findByReferralCode("JUAN-01")).thenReturn(Optional.of(member));

        assertThat(resolver().resolve("juan-01").orElseThrow().member()).isSameAs(member);
    }

    @Test
    void blankCodeResolvesToNothing() {
        assertThat(resolver().resolve("  ")).isEmpty();
    }
}
