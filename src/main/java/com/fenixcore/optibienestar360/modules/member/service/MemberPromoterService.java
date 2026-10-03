package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.member.dto.MemberPromoterAssignmentDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.entity.MemberPromoterAssignment;
import com.fenixcore.optibienestar360.modules.member.repository.MemberPromoterAssignmentRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.member.event.MemberPromoterReassignedEvent;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MemberPromoterAssignmentDto assign(UUID memberUuid, UUID promoterUuid,
                                              String reason, UUID actorUserUuid) {
        return assign(memberUuid, promoterUuid, null, reason, actorUserUuid);
    }

    /**
     * Resolves the target promoter by UUID or referral code (exactly one is
     * expected — validated by {@code AssignPromoterRequest}) and reassigns
     * the link. Also the only path to give a member their <i>first</i>
     * promoter when they were enrolled without one ({@code from == null}).
     */
    @Transactional
    @Auditable(entity = "member_promoter", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public MemberPromoterAssignmentDto assign(UUID memberUuid, UUID promoterUuid, String referralCode,
                                              String reason, UUID actorUserUuid) {
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        Promoter target = resolveTarget(promoterUuid, referralCode);
        if (!target.isActive()) {
            throw new IllegalArgumentException("promoter.inactive");
        }
        Promoter from = member.getPromoter();
        if (from != null && from.getId().equals(target.getId())) {
            // No-op reassignment — reject rather than write a noise audit row.
            throw new IllegalArgumentException("member.promoter.unchanged");
        }
        return toDto(reassign(member, target, reason, resolveActor(actorUserUuid)));
    }

    /**
     * Portfolio reassignment: moves the selected members to one target
     * promoter in a single transaction (all or nothing). Members already
     * attributed to the target are skipped rather than rejected, so a
     * manager can re-run a partially-overlapping selection safely.
     */
    @Transactional
    public List<MemberPromoterAssignmentDto> bulkAssign(List<UUID> memberUuids, UUID promoterUuid,
                                                        String reason, UUID actorUserUuid) {
        Promoter target = resolveTarget(promoterUuid, null);
        if (!target.isActive()) {
            throw new IllegalArgumentException("promoter.inactive");
        }
        User actor = resolveActor(actorUserUuid);
        List<MemberPromoterAssignmentDto> result = new ArrayList<>();
        for (UUID memberUuid : memberUuids) {
            Member member = memberRepository.findByUuid(memberUuid)
                    .orElseThrow(() -> new NoSuchElementException("member.not_found"));
            if (member.getPromoter() != null && member.getPromoter().getId().equals(target.getId())) {
                continue;
            }
            result.add(toDto(reassign(member, target, reason, actor)));
        }
        return result;
    }

    /**
     * Moves a promoter's whole portfolio up the hierarchy (roll-up): to the
     * nearest active supervisor in their chain, or to the {@code INSTITUCION}
     * system promoter when there is none — the usual step when a promoter
     * leaves. Their past collections stay theirs ({@code payments.promoter_id}
     * is a registration-time snapshot); only future attribution moves.
     */
    @Transactional
    public List<MemberPromoterAssignmentDto> reassignPortfolioToSupervisor(UUID sourcePromoterUuid, String reason,
                                                                           UUID actorUserUuid) {
        Promoter source = promoterRepository.findByUuid(sourcePromoterUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        Promoter target = nearestActiveSupervisor(source)
                .or(() -> promoterRepository.findByReferralCode(PromoterResolver.SYSTEM_PROMOTER_CODE)
                        .filter(Promoter::isActive))
                .orElseThrow(() -> new NoSuchElementException("promoter.supervisor.not_found"));
        User actor = resolveActor(actorUserUuid);
        List<MemberPromoterAssignmentDto> result = new ArrayList<>();
        for (Member member : memberRepository.findByPromoter_Id(source.getId())) {
            result.add(toDto(reassign(member, target, reason, actor)));
        }
        return result;
    }

    private static Optional<Promoter> nearestActiveSupervisor(Promoter promoter) {
        Set<Long> visited = new HashSet<>();
        Promoter current = promoter.getSupervisor();
        while (current != null && visited.add(current.getId())) {
            if (current.isActive()) {
                return Optional.of(current);
            }
            current = current.getSupervisor();
        }
        return Optional.empty();
    }

    private MemberPromoterAssignment reassign(Member member, Promoter target, String reason, User actor) {
        Promoter from = member.getPromoter();
        member.setPromoter(target);  // managed → dirty-check flushes on commit

        MemberPromoterAssignment record = new MemberPromoterAssignment();
        record.setMember(member);
        record.setFromPromoter(from);
        record.setToPromoter(target);
        record.setActor(actor);
        record.setReason(reason);
        MemberPromoterAssignment saved = assignmentRepository.save(record);

        if (!target.isSystem() && member.getPerson() != null) {
            Person person = member.getPerson();
            Person advisor = target.getPerson();
            eventPublisher.publishEvent(new MemberPromoterReassignedEvent(
                    person.getEmail(), person.getFullName(), person.getLocale(),
                    target.getDisplayName(),
                    advisor != null ? advisor.getPhone() : null,
                    advisor != null ? advisor.getEmail() : null));
        }
        return saved;
    }

    private User resolveActor(UUID actorUserUuid) {
        return actorUserUuid == null ? null : userRepository.findByUuid(actorUserUuid).orElse(null);
    }

    private Promoter resolveTarget(UUID promoterUuid, String referralCode) {
        if (promoterUuid != null) {
            return promoterRepository.findByUuid(promoterUuid)
                    .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        }
        String normalized = referralCode.trim().toUpperCase();
        return promoterRepository.findByReferralCode(normalized)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
    }

    /** Reassignment history of a member, newest first. */
    public List<MemberPromoterAssignmentDto> history(UUID memberUuid) {
        if (!memberRepository.existsByUuid(memberUuid)) {
            throw new NoSuchElementException("member.not_found");
        }
        return assignmentRepository.findByMember_UuidOrderByCreatedAtDesc(memberUuid).stream()
                .map(this::toDto)
                .toList();
    }

    private MemberPromoterAssignmentDto toDto(MemberPromoterAssignment a) {
        Promoter from = a.getFromPromoter();
        Promoter to = a.getToPromoter();
        User actor = a.getActor();
        return new MemberPromoterAssignmentDto(
                a.getUuid(),
                a.getMember().getUuid(),
                a.getMember().getPerson() != null ? a.getMember().getPerson().getFullName() : null,
                from != null ? from.getUuid() : null,
                from != null ? from.getDisplayName() : null,
                to.getUuid(),
                to.getDisplayName(),
                actor != null ? actor.getUuid() : null,
                a.getReason(),
                a.getCreatedAt());
    }
}
