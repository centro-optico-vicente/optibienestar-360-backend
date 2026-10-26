package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
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
import com.fenixcore.optibienestar360.modules.terms.dto.PendingTermDto.PreviousAcceptance;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptanceHistoryDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance.Channel;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsAcceptanceRepository;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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
 *   without a linked version asks for nothing. Accepted <b>once per
 *   agreement</b> (V189): one template shared by two of the user's allies is
 *   two pending items, each rendered with its own agreement's data. Allies
 *   come primary first, then by name (same order as {@code MyAlliesService}).</li>
 * </ul>
 * {@code is_public} plays no part here — it only controls the public web
 * endpoint. Each pending text is rendered with the variables of the entity
 * that made it due ({@link TermsVariableResolver}), and that exact text is
 * stored with its SHA-256 when accepted.
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
     * ADVISOR, then ALLY (one per agreement). Empty list = nothing pending.
     */
    public List<PendingTermDto> pendingForUser(UUID userUuid) {
        User user = requireUser(userUuid);
        VersionNumbers numbers = new VersionNumbers();
        return pendingDues(user).stream().map(due -> toPendingDto(user, due, numbers)).toList();
    }

    /**
     * Default of {@code is_mandatory} for a new version created without it
     * (V192): MEMBER {@code true}, ADVISOR / ALLY {@code false}. The flag is
     * per version afterwards — an optional contract can be postponed, gating
     * only its own module ({@link #hasPending}).
     */
    public static boolean isMandatoryByDefault(TermType type) {
        return type == TermType.MEMBER;
    }

    /**
     * Whether the caller still has a contract of {@code type} to accept —
     * backs {@code TermsGateInterceptor}. Same resolution as
     * {@link #pendingForUser} but without rendering any text, and {@code false}
     * (never an error) for an unknown user.
     */
    public boolean hasPending(UUID userUuid, TermType type) {
        // A caller with no user row has nothing pending: the gate must not turn
        // that into an error of its own — the endpoint answers it as usual.
        return userRepository.findByUuid(userUuid)
                .map(user -> pendingDues(user).stream().anyMatch(due -> due.version.getTermType() == type))
                .orElse(false);
    }

    /**
     * {@code GET /v1/me/terms/acceptances} — every contract the caller
     * accepted, newest first, with the text as accepted (legacy rows without
     * a stored text are re-rendered with current data and flagged).
     */
    public List<TermsAcceptanceHistoryDto> historyForUser(UUID userUuid) {
        User user = requireUser(userUuid);
        VersionNumbers numbers = new VersionNumbers();
        return termsAcceptanceRepository.findByUserIdOrderByAcceptedAtDescIdDesc(user.getId()).stream()
                .map(acceptance -> {
                    TermsVersion version = acceptance.getTermsVersion();
                    AllyAgreement agreement = acceptance.getAllyAgreement();
                    Ally ally = agreement == null ? null : agreement.getAlly();
                    boolean reconstructed = acceptance.getRenderedContent() == null;
                    return new TermsAcceptanceHistoryDto(
                            acceptance.getUuid(),
                            version.getTermType(),
                            version.getUuid(),
                            numbers.of(version),
                            version.getTitle(),
                            ally == null ? null : ally.getUuid(),
                            ally == null ? null : ally.getName(),
                            agreement == null ? null : agreement.getUuid(),
                            acceptance.getAcceptedAt(),
                            acceptance.getChannel(),
                            acceptedText(user, acceptance),
                            reconstructed);
                })
                .toList();
    }

    /**
     * A contract the user must accept: the version, the agreement that made
     * it due (ALLY only) and the (lazily built, memoized) rendered text.
     */
    private final class Due {
        private final TermsVersion version;
        private final AllyAgreement agreement;
        private final Supplier<Map<String, String>> values;
        private String rendered;

        private Due(TermsVersion version, AllyAgreement agreement, Supplier<Map<String, String>> values) {
            this.version = version;
            this.agreement = agreement;
            this.values = values;
        }

        String rendered() {
            if (rendered == null) {
                rendered = renderer.render(version.getContentMarkdown(), values.get());
            }
            return rendered;
        }

        boolean matches(UUID versionUuid, UUID agreementUuid) {
            return version.getUuid().equals(versionUuid)
                    && Objects.equals(agreement == null ? null : agreement.getUuid(), agreementUuid);
        }
    }

    /** Every contract due for {@code user}, in display order, minus the ones already accepted. */
    private List<Due> pendingDues(User user) {
        Instant now = Instant.now();
        List<Due> required = new ArrayList<>();
        currentOf(TermType.MEMBER, now)
                .ifPresent(v -> required.add(new Due(v, null, () -> variableResolver.forMember(user))));
        activeAdvisor(user.getUuid()).ifPresent(advisor -> currentOf(TermType.ADVISOR, now)
                .ifPresent(v -> required.add(new Due(v, null, () -> variableResolver.forAdvisor(advisor)))));
        for (AllyAgreement agreement : allyAgreementsWithVigentTemplate(user.getUuid(), now)) {
            required.add(new Due(agreement.getTermsVersion(), agreement, () -> variableResolver.forAlly(agreement)));
        }
        return required.stream().filter(due -> !isAccepted(user, due)).toList();
    }

    private boolean isAccepted(User user, Due due) {
        Long versionId = due.version.getId();
        return due.agreement == null
                ? termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(user.getId(), versionId)
                : termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementId(user.getId(), versionId,
                        due.agreement.getId());
    }

    private PendingTermDto toPendingDto(User user, Due due, VersionNumbers numbers) {
        TermsVersion v = due.version;
        Ally ally = due.agreement == null ? null : due.agreement.getAlly();
        PreviousAcceptance previous = previousAcceptance(user, due)
                .map(a -> new PreviousAcceptance(a.getTermsVersion().getUuid(), numbers.of(a.getTermsVersion()),
                        a.getAcceptedAt(), acceptedText(user, a)))
                .orElse(null);
        return new PendingTermDto(v.getUuid(), v.getTermType(), v.getTitle(), due.rendered(), numbers.of(v),
                ally == null ? null : ally.getUuid(),
                ally == null ? null : ally.getName(),
                due.agreement == null ? null : due.agreement.getUuid(),
                previous,
                v.isMandatory());
    }

    /**
     * The acceptance a pending item replaces: for ALLY, the latest one of the
     * same agreement, else of the same ally (the agreement was renewed), else
     * an unlinked legacy acceptance of the same version (pre-V189, shared
     * template); for MEMBER / ADVISOR, the latest one of the same type.
     */
    private Optional<TermsAcceptance> previousAcceptance(User user, Due due) {
        if (due.agreement == null) {
            return termsAcceptanceRepository
                    .findFirstByUserIdAndTermsVersionTermTypeAndAllyAgreementIsNullOrderByAcceptedAtDescIdDesc(
                            user.getId(), due.version.getTermType());
        }
        Optional<TermsAcceptance> sameAgreement = termsAcceptanceRepository
                .findFirstByUserIdAndAllyAgreementIdOrderByAcceptedAtDescIdDesc(user.getId(), due.agreement.getId());
        if (sameAgreement.isPresent()) return sameAgreement;
        Ally ally = due.agreement.getAlly();
        if (ally != null && ally.getId() != null) {
            Optional<TermsAcceptance> sameAlly = termsAcceptanceRepository
                    .findFirstByUserIdAndAllyAgreementAllyIdOrderByAcceptedAtDescIdDesc(user.getId(), ally.getId());
            if (sameAlly.isPresent()) return sameAlly;
        }
        return termsAcceptanceRepository
                .findFirstByUserIdAndTermsVersionIdAndAllyAgreementIsNullOrderByAcceptedAtDescIdDesc(
                        user.getId(), due.version.getId());
    }

    /**
     * The text of {@code acceptance}: the stored one, or — for rows recorded
     * before V189 — its version's template rendered with the current data of
     * the entity it belongs to (best effort, flagged as reconstructed).
     */
    private String acceptedText(User user, TermsAcceptance acceptance) {
        if (acceptance.getRenderedContent() != null) {
            return acceptance.getRenderedContent();
        }
        TermsVersion version = acceptance.getTermsVersion();
        Map<String, String> values = switch (version.getTermType()) {
            case MEMBER -> variableResolver.forMember(user);
            case ADVISOR -> activeAdvisor(user.getUuid()).map(variableResolver::forAdvisor).orElse(Map.of());
            case ALLY -> acceptance.getAllyAgreement() == null
                    ? Map.of() : variableResolver.forAlly(acceptance.getAllyAgreement());
        };
        return renderer.render(version.getContentMarkdown(), values);
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
     * already-vigente template — primary ally first, then by ally name, then
     * by agreement start date. A linked version not yet vigente is skipped
     * until it is.
     */
    private List<AllyAgreement> allyAgreementsWithVigentTemplate(UUID userUuid, Instant now) {
        Map<Long, Integer> allyOrder = new LinkedHashMap<>();
        allyUserRepository.findActiveByUserUuid(userUuid).stream()
                .sorted(Comparator.comparing(AllyUser::isPrimary).reversed()
                        .thenComparing(au -> nameOf(au.getAlly()), String.CASE_INSENSITIVE_ORDER))
                .map(AllyUser::getAlly)
                .map(Ally::getId)
                .forEach(id -> allyOrder.putIfAbsent(id, allyOrder.size()));
        if (allyOrder.isEmpty()) return List.of();
        return allyAgreementRepository.findInForceWithTermsVersionByAllyIds(List.copyOf(allyOrder.keySet()),
                        ACTIVE_AGREEMENT_STATUS).stream()
                .filter(a -> a.getTermsVersion() != null)
                .filter(a -> !a.getTermsVersion().getValidFrom().isAfter(now))
                .sorted(Comparator.<AllyAgreement>comparingInt(a -> allyOrder.getOrDefault(a.getAlly().getId(),
                                Integer.MAX_VALUE))
                        .thenComparing(AllyAgreement::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AllyAgreement::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static String nameOf(Ally ally) {
        return ally == null || ally.getName() == null ? "" : ally.getName();
    }

    /**
     * Accepts the given pending items for the caller — transactional and
     * idempotent (an item already accepted is skipped, and the partial unique
     * indexes of V189 absorb a racing duplicate submit). Each item is
     * re-validated as actually pending for this user, so a caller can't
     * blindly accept an arbitrary/superseded version by guessing a UUID:
     * 422 {@code terms_acceptance.not_pending}. An item without
     * {@code allyAgreementUuid} (legacy {@code termsVersionUuids}) is
     * accepted only when it matches exactly one pending item; an ALIADO
     * version pending for several agreements is 422
     * {@code terms_acceptance.agreement_required}.
     * Recorded with channel {@code PORTAL}, the caller's IP/User-Agent as
     * evidence and the exact text shown (plus its SHA-256).
     */
    @Transactional
    public void accept(UUID userUuid, List<TermsAcceptRequest.Item> items, ClientRequestMetadata client) {
        User user = requireUser(userUuid);
        List<Due> pending = pendingDues(user);
        Set<Due> toSave = new LinkedHashSet<>();
        for (TermsAcceptRequest.Item item : items) {
            toSave.add(resolvePending(pending, item));
        }
        for (Due due : toSave) {
            save(user, due.version, due.agreement, due.rendered(), Channel.PORTAL, client, null, null);
        }
    }

    /**
     * {@code POST /v1/me/terms/{termsVersionUuid}/accept} — one pending item,
     * same rules as {@link #accept(UUID, List, ClientRequestMetadata)}
     * ({@code allyAgreementUuid} only for ALIADO).
     */
    @Transactional
    public void acceptOne(UUID userUuid, UUID termsVersionUuid, UUID allyAgreementUuid, ClientRequestMetadata client) {
        accept(userUuid, List.of(new TermsAcceptRequest.Item(termsVersionUuid, allyAgreementUuid)), client);
    }

    /** Legacy entry point: bare version UUIDs (see {@link #accept(UUID, List, ClientRequestMetadata)}). */
    @Transactional
    public void acceptVersions(UUID userUuid, List<UUID> termsVersionUuids, ClientRequestMetadata client) {
        accept(userUuid, termsVersionUuids.stream().map(uuid -> new TermsAcceptRequest.Item(uuid, null)).toList(),
                client);
    }

    private static Due resolvePending(List<Due> pending, TermsAcceptRequest.Item item) {
        if (item.allyAgreementUuid() != null) {
            return pending.stream()
                    .filter(due -> due.matches(item.termsVersionUuid(), item.allyAgreementUuid()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("terms_acceptance.not_pending"));
        }
        List<Due> sameVersion = pending.stream()
                .filter(due -> due.version.getUuid().equals(item.termsVersionUuid()))
                .toList();
        if (sameVersion.isEmpty()) {
            throw new IllegalArgumentException("terms_acceptance.not_pending");
        }
        if (sameVersion.size() > 1) {
            throw new IllegalArgumentException("terms_acceptance.agreement_required");
        }
        return sameVersion.getFirst();
    }

    /**
     * Records the acceptance given on the public affiliation form, right after
     * the AFILIADO user and its membership are created (same transaction).
     * The caller has already validated {@code version} as the current public
     * AFILIADO version ({@code TermsVersionService.requireCurrentPublic}).
     * The stored text is rendered here with the new member's data; use the
     * {@linkplain #recordSelfServiceAcceptance(User, TermsVersion, Membership, ClientRequestMetadata, Instant, String)
     * full overload} when the caller already holds the exact text the person saw.
     */
    @Transactional
    public void recordSelfServiceAcceptance(User user, TermsVersion version, Membership membership,
                                            ClientRequestMetadata client) {
        recordSelfServiceAcceptance(user, version, membership, client, null, null);
    }

    /**
     * {@link #recordSelfServiceAcceptance(User, TermsVersion, Membership, ClientRequestMetadata)}
     * for an acceptance given before it is recorded (e.g. on a form reviewed
     * later): {@code acceptedAt} is when the person accepted ({@code null} =
     * now) and {@code renderedContent} the exact text they saw ({@code null} =
     * render it with the member's current data).
     */
    @Transactional
    public void recordSelfServiceAcceptance(User user, TermsVersion version, Membership membership,
                                            ClientRequestMetadata client, Instant acceptedAt,
                                            String renderedContent) {
        recordFormAcceptance(user, version, membership, client, acceptedAt, renderedContent, Channel.SELF_SERVICE);
    }

    /**
     * {@link #recordSelfServiceAcceptance(User, TermsVersion, Membership, ClientRequestMetadata, Instant, String)}
     * with an explicit {@code channel}: {@code SELF_SERVICE} for the public
     * form, {@code ASSISTED} when the person accepted on the advisor's device
     * (assisted affiliation, V194). Only those two form channels are allowed.
     */
    @Transactional
    public void recordFormAcceptance(User user, TermsVersion version, Membership membership,
                                     ClientRequestMetadata client, Instant acceptedAt,
                                     String renderedContent, Channel channel) {
        if (channel != Channel.SELF_SERVICE && channel != Channel.ASSISTED) {
            throw new IllegalArgumentException("form acceptance channel must be SELF_SERVICE or ASSISTED: " + channel);
        }
        if (termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(user.getId(),
                version.getId())) {
            return;
        }
        String content = renderedContent != null
                ? renderedContent
                : renderer.render(version.getContentMarkdown(), variableResolver.forMember(user));
        save(user, version, null, content, channel, client, membership, acceptedAt);
    }

    private void save(User user, TermsVersion version, AllyAgreement agreement, String renderedContent,
                      Channel channel, ClientRequestMetadata client, Membership membership, Instant acceptedAt) {
        ClientRequestMetadata evidence = client != null ? client : ClientRequestMetadata.EMPTY;
        TermsAcceptance acceptance = new TermsAcceptance();
        acceptance.setUser(user);
        acceptance.setTermsVersion(version);
        acceptance.setAllyAgreement(agreement);
        acceptance.setAcceptedAt(acceptedAt != null ? acceptedAt : Instant.now());
        acceptance.setChannel(channel);
        acceptance.setIpAddress(evidence.ipAddress());
        acceptance.setUserAgent(evidence.userAgent());
        acceptance.setMembership(membership);
        acceptance.setRenderedContent(renderedContent);
        acceptance.setContentSha256(sha256(renderedContent));
        termsAcceptanceRepository.save(acceptance);
    }

    /** Hex SHA-256 of {@code content} (UTF-8); {@code null} for {@code null}. */
    static String sha256(String content) {
        if (content == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 not available", impossible);
        }
    }

    private User requireUser(UUID userUuid) {
        return userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
    }

    /**
     * 1-based position of a version in its stream (same type and ALIADO
     * scope), by {@code validFrom} then id — the "version N" shown to the
     * user. Loads each type's versions once per call.
     */
    private final class VersionNumbers {
        private final Map<TermType, List<TermsVersion>> byType = new EnumMap<>(TermType.class);

        int of(TermsVersion version) {
            List<TermsVersion> all = byType.computeIfAbsent(version.getTermType(),
                    termsVersionRepository::findByTermTypeOrderByValidFromDesc);
            long earlierOrSame = all.stream()
                    .filter(other -> other.sameStreamAs(version))
                    .filter(other -> isEarlierOrSame(other, version))
                    .count();
            return (int) Math.max(1, earlierOrSame);
        }

        private boolean isEarlierOrSame(TermsVersion other, TermsVersion version) {
            int byDate = other.getValidFrom().compareTo(version.getValidFrom());
            if (byDate != 0) return byDate < 0;
            if (other.getId() == null || version.getId() == null) return true;
            return other.getId() <= version.getId();
        }
    }
}
