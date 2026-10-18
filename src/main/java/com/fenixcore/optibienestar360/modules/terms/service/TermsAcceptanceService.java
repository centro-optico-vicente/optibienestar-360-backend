package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Self-service acceptance flow — resolves which contracts the caller must
 * accept, and records their acceptance. The audience of each kind:
 * <ul>
 *   <li><b>AFILIADO</b> (membership agreement): every user, whatever their
 *   roles — the current version of the type.</li>
 *   <li><b>PROMOTOR</b> (advisor mandate): users holding an effective
 *   {@code PROMOTOR} role — same active, non-expired filter
 *   {@code PermissionResolver} uses.</li>
 *   <li><b>ALIADO</b> (ally agreement): for every ally the user operates on
 *   ({@code ally_users}), the template linked to that ally's active
 *   agreements ({@code is_active} and status {@code ACTIVE}); an agreement
 *   without a linked version asks for nothing.</li>
 * </ul>
 * {@code is_public} plays no part here — it only controls the public web
 * endpoint.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsAcceptanceService {

    /** Role whose holders must accept the PROMOTOR mandate (V6 roles). */
    static final String PROMOTOR_ROLE = "PROMOTOR";

    /** {@code ally_agreements.status} of an agreement in force (V11 CHECK). */
    static final String ACTIVE_AGREEMENT_STATUS = "ACTIVE";

    private final UserRepository userRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final TermsAcceptanceRepository termsAcceptanceRepository;
    private final AllyUserRepository allyUserRepository;
    private final AllyAgreementRepository allyAgreementRepository;
    private final TermsTemplateRenderer renderer;

    /**
     * The versions the caller must accept (see the class javadoc for each
     * kind's audience) that they have not accepted yet, AFILIADO first, then
     * PROMOTOR, then ALIADO. Empty list = nothing pending.
     */
    public List<PendingTermDto> pendingForUser(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        Set<String> effectiveRoleNames = user.getUserRoles().stream()
                .filter(this::isEffective)
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        // Keyed by version id: one ALIADO template shared by two of the user's allies is accepted once.
        Map<Long, TermsVersion> required = new LinkedHashMap<>();
        currentOf(TermType.AFILIADO, now).ifPresent(v -> required.put(v.getId(), v));
        if (effectiveRoleNames.contains(PROMOTOR_ROLE)) {
            currentOf(TermType.PROMOTOR, now).ifPresent(v -> required.put(v.getId(), v));
        }
        for (TermsVersion v : allyAgreementVersions(userUuid, now)) {
            required.putIfAbsent(v.getId(), v);
        }

        return required.values().stream()
                .filter(v -> !termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), v.getId()))
                .map(v -> new PendingTermDto(v.getUuid(), v.getTermType(), v.getTitle(),
                        renderer.render(v.getContentMarkdown())))
                .toList();
    }

    private Optional<TermsVersion> currentOf(TermType type, Instant now) {
        return termsVersionRepository
                .findFirstByTermTypeAndAllyTypeIsNullAndSegmentIsNullAndAllyIsNullAndValidFromLessThanEqualOrderByValidFromDesc(
                        type, now);
    }

    /**
     * ALIADO templates linked to the active agreements of the allies the user
     * operates on. A linked version not yet vigente is skipped until it is.
     */
    private List<TermsVersion> allyAgreementVersions(UUID userUuid, Instant now) {
        List<Long> allyIds = allyUserRepository.findActiveByUserUuid(userUuid).stream()
                .map(AllyUser::getAlly)
                .map(ally -> ally.getId())
                .distinct()
                .toList();
        if (allyIds.isEmpty()) return List.of();
        return allyAgreementRepository.findInForceWithTermsVersionByAllyIds(allyIds, ACTIVE_AGREEMENT_STATUS).stream()
                .map(AllyAgreement::getTermsVersion)
                .filter(Objects::nonNull)
                .filter(v -> !v.getValidFrom().isAfter(now))
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

    private boolean isEffective(UserRole ur) {
        return ur.isActive() && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(Instant.now()));
    }
}
