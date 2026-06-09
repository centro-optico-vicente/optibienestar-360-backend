package com.fenixcore.optisaludplus.modules.membership.service;

import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.member.repository.MemberRepository;
import com.fenixcore.optisaludplus.modules.membership.dto.MembershipCreateRequest;
import com.fenixcore.optisaludplus.modules.membership.dto.MembershipDto;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import com.fenixcore.optisaludplus.modules.membership.entity.Plan;
import com.fenixcore.optisaludplus.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optisaludplus.modules.membership.repository.MembershipRepository;
import com.fenixcore.optisaludplus.modules.membership.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Application service for {@link Membership} — the enrollment + history
 * surface under {@code /v1/admin/members/{memberUuid}/memberships}. Cancel
 * and reactivate transitions live in a separate service (own bullet under
 * {@code /v1/admin/memberships/{uuid}/cancel|reactivate}); this one only
 * handles enrollment and listing.
 *
 * <p>Plural class name (MembershipsService) to mirror the convention used by
 * {@code MembersService} / {@code PlansService} / {@code AlliesService}.
 * Leaves the singular {@code MembershipService} name free for the future
 * lifecycle-transition / status-job service.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipsService {

    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipMapper mapper;

    // ─── Listing under a member ─────────────────────────────────────────────

    public List<MembershipDto> listForMember(UUID memberUuid) {
        Member member = findMember(memberUuid);
        return membershipRepository.findByMemberIdOrderByEnrolledAtDesc(member.getId()).stream()
                .map(mapper::toDto)
                .toList();
    }

    public MembershipDto get(UUID memberUuid, UUID membershipUuid) {
        return mapper.toDto(findUnderMember(memberUuid, membershipUuid));
    }

    // ─── Enroll ─────────────────────────────────────────────────────────────

    @Transactional
    public MembershipDto enroll(UUID memberUuid, MembershipCreateRequest req) {
        Member member = findMember(memberUuid);

        // V21 partial UNIQUE on (member_id) WHERE is_active=TRUE forbids two
        // active subscriptions at once. Pre-check returns clean 422 instead
        // of the misleading 409 from the unique-violation handler.
        if (membershipRepository.existsByMemberIdAndActiveTrue(member.getId())) {
            throw new IllegalArgumentException("member.membership.already_active");
        }

        Plan plan = planRepository.findByUuid(req.planUuid())
                .orElseThrow(() -> new NoSuchElementException("plan.not_found"));

        LocalDate enrolledAt = req.enrolledAt() != null ? req.enrolledAt() : LocalDate.now();

        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setEnrolledAt(enrolledAt);
        membership.setExpiresAt(req.expiresAt());
        // First payment is due one month after enrollment. Calendar arithmetic
        // matches Jan 15 → Feb 15 rather than Feb 14 from +30 days.
        membership.setNextDueDate(enrolledAt.plusMonths(1));

        // Pricing snapshot — immune to later plan edits. Renegotiation = cancel
        // this row + enroll a new one.
        membership.setInscriptionFee(plan.getInscriptionFee());
        membership.setMonthlyFee(plan.getMonthlyFee());
        membership.setGracePeriodDays(plan.getGracePeriodDays());

        // Lifecycle starts at ACTIVE. The CHECK constraint pins status to
        // exactly the four LifecycleStatus values; using the enum's name()
        // keeps the spelling type-safe.
        membership.setStatus(Membership.LifecycleStatus.ACTIVE.name());
        membership.setLastStatusChangeAt(Instant.now());
        membership.setLastStatusChangeReason("Enrolled by admin");

        return mapper.toDto(membershipRepository.save(membership));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Member findMember(UUID uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
    }

    private Membership findUnderMember(UUID memberUuid, UUID membershipUuid) {
        Membership membership = membershipRepository.findByUuid(membershipUuid)
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
        if (!membership.getMember().getUuid().equals(memberUuid)) {
            throw new NoSuchElementException("membership.not_found");
        }
        return membership;
    }
}
