package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
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
    private final ConversionEnricher conversionEnricher;
    private final com.fenixcore.optibienestar360.modules.promotion.service.PromotionAssignmentService promotionAssignmentService;
    /** Provider, not a direct reference: the credit application depends on payments, and this keeps the graph acyclic. */
    private final org.springframework.beans.factory.ObjectProvider<com.fenixcore.optibienestar360.modules.payment.service.CreditApplicationService> creditApplicationProvider;
    private final InscriptionPaymentMethodResolver inscriptionPaymentMethodResolver;

    // ─── Cross-member search (filters) ──────────────────────────────────────

    private static final java.time.ZoneId BUSINESS_ZONE = java.time.ZoneId.of("America/Caracas");
    private static final int DEFAULT_DUE_SOON_DAYS = 7;
    private static final java.util.Set<String> LIFECYCLE_STATUSES = java.util.Set.of("ACTIVE", "SUSPENDED", "EXPIRED", "CANCELED");
    /** Sort keys accepted from the client — anything else falls back to the default (soonest due date first). */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of("nextDueDate", "enrolledAt", "expiresAt", "status", "lastPaidThrough");

    public org.springframework.data.domain.Page<com.fenixcore.optibienestar360.modules.membership.dto.MembershipListItemDto> search(
            com.fenixcore.optibienestar360.modules.membership.dto.MembershipSearchCriteria c,
            org.springframework.data.domain.Pageable pageable) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
                pageable.getSort().stream().filter(o -> SORTABLE.contains(o.getProperty())).toList());
        if (sort.isEmpty()) {
            sort = org.springframework.data.domain.Sort.by("nextDueDate").ascending();
        }
        var page = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        return membershipRepository.findAll(searchSpec(c, today), page).map(m -> toListItem(m, today));
    }

    private static org.springframework.data.jpa.domain.Specification<Membership> searchSpec(
            com.fenixcore.optibienestar360.modules.membership.dto.MembershipSearchCriteria c, LocalDate today) {
        org.springframework.data.jpa.domain.Specification<Membership> spec = (root, query, cb) -> cb.conjunction();
        if (c == null) {
            return spec;
        }
        if (c.q() != null && !c.q().isBlank()) {
            spec = spec.and(com.fenixcore.optibienestar360.core.util.SearchSpecifications.acrossFields(c.q(),
                    "member.person.firstName", "member.person.lastName", "member.person.documentNumber"));
        }
        if (c.memberUuid() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("member").get("uuid"), c.memberUuid()));
        }
        if (c.promoterUuid() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("member").get("promoter").get("uuid"), c.promoterUuid()));
        }
        if (c.planUuid() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("plan").get("uuid"), c.planUuid()));
        }
        if (c.planType() != null && !c.planType().isBlank()) {
            var type = com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType.fromCode(c.planType());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("plan").get("type"), type));
        }
        if (c.enrolledFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("enrolledAt"), c.enrolledFrom()));
        }
        if (c.enrolledTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("enrolledAt"), c.enrolledTo()));
        }
        if (c.nextDueFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("nextDueDate"), c.nextDueFrom()));
        }
        if (c.nextDueTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("nextDueDate"), c.nextDueTo()));
        }
        String status = c.status() == null ? "" : c.status().trim().toUpperCase();
        if (LIFECYCLE_STATUSES.contains(status)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        } else if ("OVERDUE".equals(status)) {
            spec = spec.and((root, query, cb) -> cb.and(
                    root.get("status").in("ACTIVE", "SUSPENDED"),
                    cb.lessThan(root.get("nextDueDate"), today)));
        } else if ("DUE_SOON".equals(status)) {
            int days = c.dueSoonDays() != null && c.dueSoonDays() > 0 ? c.dueSoonDays() : DEFAULT_DUE_SOON_DAYS;
            spec = spec.and((root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), "ACTIVE"),
                    cb.between(root.get("nextDueDate"), today, today.plusDays(days))));
        } else if (!status.isEmpty()) {
            throw new IllegalArgumentException("membership.search.status_invalid");
        }
        return spec;
    }

    private com.fenixcore.optibienestar360.modules.membership.dto.MembershipListItemDto toListItem(Membership m, LocalDate today) {
        Member member = m.getMember();
        var advisor = member.getPromoter();
        boolean collectible = "ACTIVE".equals(m.getStatus()) || "SUSPENDED".equals(m.getStatus());
        boolean overdue = collectible && m.getNextDueDate() != null && m.getNextDueDate().isBefore(today);
        boolean dueSoon = "ACTIVE".equals(m.getStatus()) && !overdue && m.getNextDueDate() != null
                && !m.getNextDueDate().isAfter(today.plusDays(DEFAULT_DUE_SOON_DAYS));
        String paymentStatus = !collectible ? null : overdue ? "OVERDUE" : dueSoon ? "DUE_SOON" : "UP_TO_DATE";
        return new com.fenixcore.optibienestar360.modules.membership.dto.MembershipListItemDto(
                m.getUuid(), member.getUuid(), m.getContractNumber(),
                com.fenixcore.optibienestar360.core.display.DisplayRefs.ref(member),
                advisor == null ? null : com.fenixcore.optibienestar360.core.display.DisplayRefs.ref(advisor),
                m.getPlan().getUuid(), m.getPlan().getCode(), m.getPlan().getName(), m.getPlan().getType(),
                m.getEnrolledAt(), m.getNextDueDate(), m.getLastPaidThrough(),
                m.getMonthlyFee(), m.getCurrency().getCode(), m.getStatus(), paymentStatus,
                overdue ? java.time.temporal.ChronoUnit.DAYS.between(m.getNextDueDate(), today) : 0);
    }

    // ─── Listing under a member ─────────────────────────────────────────────

    public List<MembershipDto> listForMember(UUID memberUuid) {
        Member member = findMember(memberUuid);
        return membershipRepository.findByMemberIdOrderByEnrolledAtDesc(member.getId()).stream()
                .map(this::enrich)
                .toList();
    }

    public MembershipDto get(UUID memberUuid, UUID membershipUuid) {
        return enrich(findUnderMember(memberUuid, membershipUuid));
    }

    /**
     * Live-converts {@link Membership#getMonthlyFee()} to the org's official currency (ADR 0015 §6 Caso B)
     * and adds the inscription payment method ("Modalidad de pago", hub ADR 0031 §14).
     */
    private MembershipDto enrich(Membership membership) {
        var conv = conversionEnricher.toOfficial(membership.getMonthlyFee(), membership.getCurrency());
        return mapper.toDto(membership)
                .withConversion(conv.amountConverted(), conv.currencyCode(), conv.rate(), conv.rateDate())
                .withInscriptionPaymentMethod(inscriptionPaymentMethodResolver.resolve(membership));
    }

    // ─── Enroll ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "membership", action = AuditAction.CREATE)
    public MembershipDto enroll(UUID memberUuid, MembershipCreateRequest req, UUID actorUuid) {
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
        // Billing cutover day (V47) — defaults to the enrollment day so behavior
        // is unchanged until an advisor explicitly moves it (e.g. "inscrito el 3,
        // cobro desde el 1"). Capped at 28 so it's valid in every month.
        membership.setBillingStartDay(Math.min(enrolledAt.getDayOfMonth(), 28));

        // Pricing snapshot — immune to later plan edits. Renegotiation = cancel
        // this row + enroll a new one.
        membership.setInscriptionFee(plan.getInscriptionFee());
        membership.setMonthlyFee(plan.getMonthlyFee());
        membership.setCurrency(plan.getCurrency());
        membership.setGracePeriodDays(plan.getGracePeriodDays());

        // Lifecycle starts at ACTIVE. The CHECK constraint pins status to
        // exactly the four LifecycleStatus values; using the enum's name()
        // keeps the spelling type-safe.
        membership.setStatus(Membership.LifecycleStatus.ACTIVE.name());
        membership.setLastStatusChangeAt(Instant.now());
        membership.setLastStatusChangeReason("Enrolled by admin");

        Membership saved = membershipRepository.save(membership);
        if (req.promotionUuid() != null) {
            promotionAssignmentService.assign(saved, req.promotionUuid(), req.promotionCode(),
                    com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Origin.ENROLLMENT, actorUuid);
        }
        scheduleCreditApplication(saved.getId());
        return mapper.toDto(saved);
    }

    /**
     * The new membership may already have credit waiting (a person who paid before enrolling): apply it once the
     * enrollment is committed, in its own transaction, so a failure there can never undo the enrollment.
     */
    private void scheduleCreditApplication(Long membershipId) {
        Runnable apply = () -> creditApplicationProvider.ifAvailable(service -> service.applyCreditSafely(membershipId));
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            apply.run();
                        }
                    });
        } else {
            apply.run();
        }
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
