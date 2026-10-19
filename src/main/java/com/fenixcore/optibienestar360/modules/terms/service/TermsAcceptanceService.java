package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.PendingTermDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance.Channel;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsAcceptanceRepository;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Self-service acceptance flow — resolves which contracts the caller must
 * accept, and records their acceptance. The audience of each kind is decided
 * by the entity the user is linked to, never by role name:
 * <ul>
 *   <li><b>MEMBER</b> (membership agreement): every user, whatever their
 *   roles — the current version of the type.</li>
 *   <li><b>ADVISOR</b> (advisor mandate): users with an active, non-system
 *   advisor record ({@code promoters.user_id}, {@code is_active}, not
 *   {@code is_system} — so never the INSTITUCION advisor), whatever their
 *   rank (asesor, supervisor, coordinador).</li>
 *   <li><b>ALLY</b> (ally agreement): for every ally the user operates on
 *   ({@code ally_users}), the template linked to that ally's active
 *   agreements ({@code is_active} and status {@code ACTIVE}); an agreement
 *   without a linked version asks for nothing.</li>
 * </ul>
 * {@code is_public} plays no part here — it only controls the public web
 * endpoint. Each pending text is rendered with the variables of the entity
 * that made it due ({@link TermsVariableResolver}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsAcceptanceService {

    /** {@code ally_agreements.status} of an agreement in force (V11 CHECK). */
    static final String ACTIVE_AGREEMENT_STATUS = "ACTIVE";

    private final UserRepository userRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final TermsAcceptanceRepository termsAcceptanceRepository;
    private final AllyUserRepository allyUserRepository;
    private final AllyAgreementRepository allyAgreementRepository;
    private final PromoterRepository promoterRepository;
    private final TermsTemplateRenderer renderer;
    private final TermsVariableResolver variableResolver;

    /**
     * The versions the caller must accept (see the class javadoc for each
     * kind's audience) that they have not accepted yet, MEMBER first, then
     * ADVISOR, then ALLY. Empty list = nothing pending.
     */
    public List<PendingTermDto> pendingForUser(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        Instant now = Instant.now();
        // Keyed by version id: one ALLY template shared by two of the user's allies is accepted once
        // (rendered with the first agreement's data).
        Map<Long, Due> required = new LinkedHashMap<>();
        currentOf(TermType.MEMBER, now)
                .ifPresent(v -> required.put(v.getId(), new Due(v, () -> variableResolver.forMember(user))));
        activeAdvisor(userUuid).ifPresent(advisor -> currentOf(TermType.ADVISOR, now)
                .ifPresent(v -> required.put(v.getId(), new Due(v, () -> variableResolver.forAdvisor(advisor)))));
        for (AllyAgreement agreement : allyAgreementsWithVigentTemplate(userUuid, now)) {
            TermsVersion v = agreement.getTermsVersion();
            required.putIfAbsent(v.getId(), new Due(v, () -> variableResolver.forAlly(agreement)));
        }

        return required.values().stream()
                .filter(due -> !termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), due.version().getId()))
                .map(due -> new PendingTermDto(due.version().getUuid(), due.version().getTermType(),
                        due.version().getTitle(),
                        renderer.render(due.version().getContentMarkdown(), due.values().get())))
                .toList();
    }

    /** A version the user must accept, plus the (lazily built) variables it is rendered with. */
    private record Due(TermsVersion version, Supplier<Map<String, String>> values) {
    }

    private Optional<TermsVersion> currentOf(TermType type, Instant now) {
        return termsVersionRepository
                .findFirstByTermTypeAndAllyTypeIsNullAndSegmentIsNullAndAllyIsNullAndValidFromLessThanEqualOrderByValidFromDesc(
                        type, now);
    }

    /** The user's active advisor record, unless it is a system advisor (INSTITUCION). */
    private Optional<Promoter> activeAdvisor(UUID userUuid) {
        return promoterRepository.findActiveByUserUuid(userUuid).filter(p -> !p.isSystem());
    }

    /**
     * Active agreements of the allies the user operates on that link an
     * already-vigente template. A linked version not yet vigente is skipped
     * until it is.
     */
    private List<AllyAgreement> allyAgreementsWithVigentTemplate(UUID userUuid, Instant now) {
        List<Long> allyIds = allyUserRepository.findActiveByUserUuid(userUuid).stream()
                .map(AllyUser::getAlly)
                .map(ally -> ally.getId())
                .distinct()
                .toList();
        if (allyIds.isEmpty()) return List.of();
        return allyAgreementRepository.findInForceWithTermsVersionByAllyIds(allyIds, ACTIVE_AGREEMENT_STATUS).stream()
                .filter(a -> a.getTermsVersion() != null)
                .filter(a -> !a.getTermsVersion().getValidFrom().isAfter(now))
                .toList();
    }

    /**
     * Accepts every {@code termsVersionUuid} in the request for the caller —
     * transactional and idempotent (the UNIQUE constraint on
     * {@code terms_acceptances} absorbs a duplicate submit). Each UUID is
     * re-validated as an actually-pending version for this user, so a caller
     * can't blindly accept an arbitrary/superseded version by guessing a UUID.
     * Recorded with channel {@code PORTAL} plus the caller's IP/User-Agent as
     * evidence.
     */
    @Transactional
    public void accept(UUID userUuid, List<UUID> termsVersionUuids, ClientRequestMetadata client) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        Set<UUID> pendingUuids = pendingForUser(userUuid).stream()
                .map(PendingTermDto::termsVersionUuid)
                .collect(Collectors.toSet());

        for (UUID versionUuid : termsVersionUuids) {
            if (!pendingUuids.contains(versionUuid)) {
                throw new IllegalArgumentException("terms_acceptance.not_pending");
            }
            TermsVersion version = termsVersionRepository.findByUuid(versionUuid)
                    .orElseThrow(() -> new NoSuchElementException("terms_version.not_found"));
            if (termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), version.getId())) {
                continue;
            }
            save(user, version, Channel.PORTAL, client, null);
        }
    }

    /**
     * Records the acceptance given on the public affiliation form, right after
     * the AFILIADO user and its membership are created (same transaction).
     * The caller has already validated {@code version} as the current public
     * AFILIADO version ({@code TermsVersionService.requireCurrentPublic}).
     */
    @Transactional
    public void recordSelfServiceAcceptance(User user, TermsVersion version, Membership membership,
                                            ClientRequestMetadata client) {
        if (termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), version.getId())) {
            return;
        }
        save(user, version, Channel.SELF_SERVICE, client, membership);
    }

    private void save(User user, TermsVersion version, Channel channel, ClientRequestMetadata client,
                      Membership membership) {
        ClientRequestMetadata evidence = client != null ? client : ClientRequestMetadata.EMPTY;
        TermsAcceptance acceptance = new TermsAcceptance();
        acceptance.setUser(user);
        acceptance.setTermsVersion(version);
        acceptance.setAcceptedAt(Instant.now());
        acceptance.setChannel(channel);
        acceptance.setIpAddress(evidence.ipAddress());
        acceptance.setUserAgent(evidence.userAgent());
        acceptance.setMembership(membership);
        termsAcceptanceRepository.save(acceptance);
    }
}
