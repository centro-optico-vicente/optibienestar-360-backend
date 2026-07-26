package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.member.dto.MemberPromoterAssignmentDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.entity.MemberPromoterAssignment;
import com.fenixcore.optibienestar360.modules.member.repository.MemberPromoterAssignmentRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns the reassignment of the permanent member↔promoter link (v2 PDF 2.a).
 * The link is set once at enrollment ({@code MembersService.create}) and only
 * ever changes here, via {@code POST /v1/admin/members/{uuid}/assign-promoter}.
 *
 * <p>Every reassignment writes one {@link MemberPromoterAssignment} audit row
 * (from → to, actor, reason). Reassigning does <b>not</b> touch already-earned
 * commissions — per PDF 2.a it only redirects future attribution, so no
 * retroactive recompute happens here.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberPromoterService {

    private final MemberRepository memberRepository;
    private final PromoterRepository promoterRepository;
    private final UserRepository userRepository;
    private final MemberPromoterAssignmentRepository assignmentRepository;

    @Transactional
    public MemberPromoterAssignmentDto assign(UUID memberUuid, UUID promoterUuid,
                                              String reason, UUID actorUserUuid) {
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        Promoter target = promoterRepository.findByUuid(promoterUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        if (!target.isActive()) {
            throw new IllegalArgumentException("promoter.inactive");
        }

        Promoter from = member.getPromoter();
        if (from != null && from.getId().equals(target.getId())) {
            // No-op reassignment — reject rather than write a noise audit row.
            throw new IllegalArgumentException("member.promoter.unchanged");
        }

        User actor = actorUserUuid == null ? null
                : userRepository.findByUuid(actorUserUuid).orElse(null);

        member.setPromoter(target);  // managed → dirty-check flushes on commit

        MemberPromoterAssignment record = new MemberPromoterAssignment();
        record.setMember(member);
        record.setFromPromoter(from);
        record.setToPromoter(target);
        record.setActor(actor);
        record.setReason(reason);
        MemberPromoterAssignment saved = assignmentRepository.save(record);

        return new MemberPromoterAssignmentDto(
                saved.getUuid(),
                member.getUuid(),
                member.getPerson() != null ? member.getPerson().getFullName() : null,
                from != null ? from.getUuid() : null,
                from != null ? from.getDisplayName() : null,
                target.getUuid(),
                target.getDisplayName(),
                actor != null ? actor.getUuid() : null,
                saved.getReason(),
                saved.getCreatedAt());
    }
}
