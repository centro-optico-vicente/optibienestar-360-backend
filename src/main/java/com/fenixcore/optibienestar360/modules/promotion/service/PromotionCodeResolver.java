package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves a promotion code typed at enrollment to who issued it: an active
 * promoter, a member (referral code) or an active ally — in that order, the
 * same single field for all three (hub ADR 0018 §7).
 */
@Component
@RequiredArgsConstructor
public class PromotionCodeResolver {

    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;
    private final AllyRepository allyRepository;

    public Optional<CodeOwner> resolve(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) return Optional.empty();
        String code = rawCode.trim().toUpperCase();
        Optional<Promoter> promoter = promoterRepository.findByReferralCode(code).filter(Promoter::isActive);
        if (promoter.isPresent()) return Optional.of(new CodeOwner(code, promoter.get(), null, null));
        Optional<Member> member = memberRepository.findByReferralCode(code).filter(Member::isActive);
        if (member.isPresent()) return Optional.of(new CodeOwner(code, null, member.get(), null));
        return allyRepository.findByReferralCode(code).filter(Ally::isActive)
                .map(ally -> new CodeOwner(code, null, null, ally));
    }

    /** Exactly one of {@code promoter}/{@code member}/{@code ally} is set. */
    public record CodeOwner(String code, Promoter promoter, Member member, Ally ally) {

        public boolean isAcceptedBy(Promotion promotion) {
            return (promoter != null && promotion.isAcceptsPromoterCode())
                    || (member != null && promotion.isAcceptsMemberCode())
                    || (ally != null && promotion.isAcceptsAllyCode());
        }
    }
}
