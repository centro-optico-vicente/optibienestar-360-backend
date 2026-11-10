package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Who may edit the ally of an advisor-assisted capture (hub ADR 0037 §5) while it is still a draft.
 *
 * <p>An advisor-captured ally has no login yet, so the portal's membership check
 * ({@code ally_users}) cannot authorise anyone. Instead the <em>capturing advisor</em> may read and
 * edit it through the same profile / licenses / opening-hours endpoints, but only while the request
 * is a draft: channel {@code ADVISOR}, {@code PENDING} and not yet sent to review. After the
 * submit the contact person (who gets a login then) is the owner and the advisor only reads it in
 * "Mis aliados".</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyDraftAccess {

    private final AllyRepository allyRepository;
    private final PromoterRepository promoterRepository;

    /** The draft ally the caller captured, or empty when it is not theirs / not a draft / unknown. */
    public Optional<Ally> findOwnDraft(UUID allyUuid, UUID actorUserUuid) {
        if (allyUuid == null || actorUserUuid == null) {
            return Optional.empty();
        }
        return promoterRepository.findActiveByUserUuid(actorUserUuid)
                .flatMap(advisor -> allyRepository.findByUuid(allyUuid).filter(a -> isOwnDraft(a, advisor)));
    }

    /** Same as {@link #findOwnDraft} but 404 {@code ally_request.not_found}: another advisor's draft never shows. */
    public Ally requireOwnDraft(UUID allyUuid, UUID actorUserUuid) {
        return findOwnDraft(allyUuid, actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_request.not_found"));
    }

    public static boolean isOwnDraft(Ally ally, Promoter advisor) {
        return ally.getRegistrationChannel() == Ally.RegistrationChannel.ADVISOR
                && ally.getRegistrationStatus() == Ally.RegistrationStatus.PENDING
                && ally.getRegistrationSubmittedAt() == null
                && ally.getCapturedByPromoter() != null
                && ally.getCapturedByPromoter().getId().equals(advisor.getId());
    }
}
