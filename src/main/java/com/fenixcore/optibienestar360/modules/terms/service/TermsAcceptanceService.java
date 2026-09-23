package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.PendingTermDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsAcceptanceRepository;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Self-service acceptance flow — resolves which of the caller's role-scoped
 * T&C are pending, and records their acceptance. Mirrors the effective-role
 * filtering {@code PermissionResolver} uses (active, non-expired assignments),
 * matched directly against {@link TermType} names since a {@code TermType}
 * literally is a role name (see {@link TermsVersion}'s javadoc).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsAcceptanceService {

    private final UserRepository userRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final TermsAcceptanceRepository termsAcceptanceRepository;

    /**
     * The current, public version of each {@link TermType} matching one of
     * the caller's effective roles, that the caller has not yet accepted.
     * Empty list = nothing pending (never blocks a user with no matching role,
     * or once everything applicable has been accepted).
     */
    public List<PendingTermDto> pendingForUser(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        Set<String> effectiveRoleNames = user.getUserRoles().stream()
                .filter(this::isEffective)
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        return Arrays.stream(TermType.values())
                .filter(type -> effectiveRoleNames.contains(type.name()))
                .map(type -> termsVersionRepository
                        .findFirstByTermTypeAndValidFromLessThanEqualOrderByValidFromDesc(type, now)
                        .orElse(null))
                .filter(v -> v != null && v.isPublic())
                .filter(v -> !termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), v.getId()))
                .map(v -> new PendingTermDto(v.getUuid(), v.getTermType(), v.getTitle(), v.getContentMarkdown()))
                .toList();
    }

    /**
     * Accepts every {@code termsVersionUuid} in the request for the caller —
     * transactional and idempotent (the UNIQUE constraint on
     * {@code terms_acceptances} absorbs a duplicate submit). Each UUID is
     * re-validated as an actually-pending version for this user, so a caller
     * can't blindly accept an arbitrary/superseded version by guessing a UUID.
     */
    @Transactional
    public void accept(UUID userUuid, List<UUID> termsVersionUuids) {
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
            TermsAcceptance acceptance = new TermsAcceptance();
            acceptance.setUser(user);
            acceptance.setTermsVersion(version);
            acceptance.setAcceptedAt(Instant.now());
            termsAcceptanceRepository.save(acceptance);
        }
    }

    private boolean isEffective(UserRole ur) {
        return ur.isActive() && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(Instant.now()));
    }
}
