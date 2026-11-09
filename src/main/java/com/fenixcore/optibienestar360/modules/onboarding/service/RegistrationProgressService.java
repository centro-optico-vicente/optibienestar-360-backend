package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.MyAllyProfileService;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.onboarding.dto.RegistrationProgressDto;
import com.fenixcore.optibienestar360.modules.onboarding.dto.RegistrationProgressDto.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the caller is in their self-registration (hub ADR 0037 §3): the steps
 * done, the percentage, whether it was submitted and whether the "Completar
 * registro" portal should still exist. The portal goes away when every step is
 * done and the request was submitted, or as soon as a staff member confirms it
 * by hand — a policy-driven (automatic) approval does not count, so the portal
 * does not vanish with optional data still empty.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationProgressService {

    public static final String KIND_MEMBER = "MEMBER";
    public static final String KIND_ALLY = "ALLY";

    private final AffiliationRequestRepository affiliationRequests;
    private final AllyUserRepository allyUsers;
    private final MyAllyProfileService allyProfiles;

    public RegistrationProgressDto get(UUID userUuid) {
        Optional<AffiliationRequest> request = affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(userUuid);
        if (request.isPresent()) {
            return memberProgress(request.get());
        }
        return allyUsers.findActiveByUserUuid(userUuid).stream()
                .filter(au -> au.getAllyRole() == AllyUser.AllyRole.OWNER)
                .map(AllyUser::getAlly)
                .filter(a -> a.getRegistrationChannel() == Ally.RegistrationChannel.SELF_SERVICE)
                .max(Comparator.comparing(Ally::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(a -> allyProgress(a, allyProfiles.completenessOf(a).missing().isEmpty()))
                .orElseGet(RegistrationProgressDto::none);
    }

    /** Affiliate: personal data and plan + contract are essential; the payment report and the family are not. */
    static RegistrationProgressDto memberProgress(AffiliationRequest r) {
        List<Step> steps = List.of(
                new Step("personal", r.getBirthDate() != null, true),
                new Step("plan", r.getPlan() != null && r.getTermsAcceptedAt() != null, true),
                new Step("payment", r.hasReportedPayment(), false),
                new Step("family", r.getFamily() != null && !r.getFamily().isEmpty(), false));
        boolean submitted = !r.isDraft();
        boolean rejected = AffiliationRequest.Status.REJECTED.name().equals(r.getStatus());
        boolean confirmed = AffiliationRequest.Status.APPROVED.name().equals(r.getStatus()) && r.getReviewedBy() != null;
        return build(KIND_MEMBER, steps, submitted, rejected, confirmed);
    }

    /** Ally: type + RIF / profession and the city are essential; the rest of the profile is not. */
    static RegistrationProgressDto allyProgress(Ally ally, boolean profileComplete) {
        boolean fiscal = !ally.getAllyTypes().isEmpty()
                && (ally.getSegment() == Segment.B2B
                        ? notBlank(ally.getTaxDocumentType()) && notBlank(ally.getTaxDocumentNumber())
                        : !ally.getProfessions().isEmpty());
        List<Step> steps = List.of(
                new Step("location", ally.getCity() != null, true),
                new Step("fiscal", fiscal, true),
                new Step("profile", profileComplete, false));
        boolean submitted = ally.getRegistrationSubmittedAt() != null;
        boolean rejected = ally.getRegistrationStatus() == Ally.RegistrationStatus.REJECTED;
        boolean confirmed = ally.getRegistrationStatus() == Ally.RegistrationStatus.APPROVED
                && ally.getApprovedBy() != null;
        return build(KIND_ALLY, steps, submitted, rejected, confirmed);
    }

    private static RegistrationProgressDto build(String kind, List<Step> steps, boolean submitted,
                                                 boolean rejected, boolean confirmed) {
        long done = steps.stream().filter(Step::done).count();
        int percent = (int) Math.round(100.0 * done / steps.size());
        boolean allDone = done == steps.size();
        boolean show = !rejected && !confirmed && (!allDone || !submitted);
        return new RegistrationProgressDto(kind, steps, percent, submitted, confirmed, show);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
