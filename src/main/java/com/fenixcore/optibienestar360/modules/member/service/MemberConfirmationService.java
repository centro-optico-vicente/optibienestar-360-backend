package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Manual confirmation of a member (project chat 2026-08-06). Most members are
 * confirmed automatically the moment their first payment is approved
 * ({@code PaymentsService.confirmMemberOnFirstApprovedPayment}), but members
 * fully covered by a subsidy (subsidy module) may never generate a payment,
 * so an admin needs a direct way to mark them confirmed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberConfirmationService {

    private final MemberRepository memberRepository;

    @Transactional
    public Instant confirm(UUID memberUuid) {
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        if (member.getConfirmedAt() != null) {
            throw new IllegalArgumentException("member.already_confirmed");
        }
        Instant now = Instant.now();
        member.setConfirmedAt(now);
        return now;
    }
}
