package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter.PromoterStatus;
import com.fenixcore.optibienestar360.modules.promoter.mapper.PromoterMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD over {@link Promoter}. Plural-name convention matches
 * {@code PlansService}, {@code MembersService}, {@code PaymentsService}.
 *
 * <p><b>System-row guard:</b> the INSTITUCION row ({@code is_system=true})
 * is seeded in V25 and cannot be created, edited, or deleted via the API.
 * Mutations targeting it surface as 422 {@code promoter.system.not_editable}
 * / {@code .not_deletable}. The V25 partial UNIQUE on
 * {@code WHERE is_system=TRUE} guarantees at most one platform-wide at the
 * DB level as a backstop.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotersService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "displayName", "referralCode", "system",
            "totalReferrals", "totalCommissionPaid",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final String[] SEARCHABLE_FIELDS = {
            "displayName", "description", "referralCode", "email", "phone"
    };

    /** Length of an auto-generated promoter code — short per PDF #4 ("ej. 6 chars"). */
    private static final int GENERATED_CODE_LENGTH = 6;

    /** Retry cap when an auto-generated candidate collides — a safety net, not a budget. */
    private static final int GENERATION_RETRIES = 10;

    private final PromoterRepository repository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final PromoterTypeRepository promoterTypeRepository;
    private final PromoterMapper mapper;
    private final SecureRandom random = new SecureRandom();

    // ─── Read ───────────────────────────────────────────────────────────────

    public PromoterDto get(UUID uuid) {
        return mapper.toDto(findManaged(uuid));
    }

    public Page<PromoterDto> list(Pageable pageable, String filter, String q) {
        Specification<Promoter> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "promoter.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(mapper::toDto);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    public PromoterDto create(PromoterCreateRequest req) {
        User user = userRepository.findByUuid(req.userUuid())
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        Person person = user.getPerson();

        // Reject if the user is already a promoter — keeps the partial
        // UNIQUE (user_id WHERE is_active) free for a clean re-create flow.
        if (repository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("promoter.user.already_assigned");
        }

        // Optional code (PDF #4): blank → auto-generate a short unique tag;
        // supplied → validate cross-table uniqueness. Either way the promoter
        // ends up with a unique referral_code used to track its affiliates.
        String referralCode = resolveReferralCode(req.referralCode());

        Promoter promoter = new Promoter();
        promoter.setDisplayName(req.displayName());
        promoter.setDescription(req.description());
        promoter.setReferralCode(referralCode);
        promoter.setSystem(false);  // API never creates system rows
        promoter.setUser(user);
        promoter.setPerson(person);
        promoter.setEmail(req.email());
        promoter.setPhone(req.phone());
        promoter.setPromoterType(resolvePromoterType(req.promoterTypeUuid()));
        promoter.setStatus(PromoterStatus.ACTIVE.name());

        return mapper.toDto(repository.save(promoter));
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Transactional
    public PromoterDto update(UUID uuid, PromoterUpdateRequest req) {
        Promoter promoter = findManaged(uuid);
        ensureNotSystemRow(promoter);

        if (req.displayName() != null) promoter.setDisplayName(req.displayName());
        if (req.description() != null) promoter.setDescription(req.description());
        if (req.email() != null)       promoter.setEmail(req.email());
        if (req.phone() != null)       promoter.setPhone(req.phone());
        if (req.promoterTypeUuid() != null) promoter.setPromoterType(resolvePromoterType(req.promoterTypeUuid()));
        if (req.active() != null)      promoter.setActive(req.active());
        if (req.status() != null)      promoter.setStatus(req.status());

        return mapper.toDto(promoter);  // dirty-check flushes on commit
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID uuid) {
        Promoter promoter = findManaged(uuid);
        ensureNotSystemRow(promoter);
        promoter.setActive(false);
        promoter.setStatus(PromoterStatus.INACTIVE.name());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Promoter findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
    }

    private PromoterType resolvePromoterType(UUID uuid) {
        if (uuid == null) return null;
        return promoterTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter_type.not_found"));
    }

    private static void ensureNotSystemRow(Promoter promoter) {
        if (promoter.isSystem()) {
            throw new IllegalArgumentException("promoter.system.not_editable");
        }
    }

    /**
     * Resolves the code for a new promoter: blank input → auto-generate a short
     * unique tag; supplied input → normalize + cross-table uniqueness pre-check
     * (promoters and members share one referral-code namespace, V27 design), for
     * a clean 422 before the V25 UNIQUE fires.
     */
    private String resolveReferralCode(String requested) {
        String code = normalize(requested);
        if (code == null) {
            return generateUniqueReferralCode();
        }
        if (repository.existsByReferralCode(code)) {
            throw new IllegalArgumentException("promoter.referral_code.duplicate");
        }
        if (memberRepository.findByReferralCode(code).isPresent()) {
            throw new IllegalArgumentException("promoter.referral_code.duplicate.member");
        }
        return code;
    }

    private String generateUniqueReferralCode() {
        for (int attempt = 0; attempt < GENERATION_RETRIES; attempt++) {
            String candidate = randomCode();
            if (!repository.existsByReferralCode(candidate)
                    && memberRepository.findByReferralCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("promoter.referral_code.generation_exhausted");
    }

    /** Uses {@link ReferralCodeService#ALPHABET} (0/O/1/I/L omitted) for print-safe codes. */
    private String randomCode() {
        StringBuilder sb = new StringBuilder(GENERATED_CODE_LENGTH);
        for (int i = 0; i < GENERATED_CODE_LENGTH; i++) {
            sb.append(ReferralCodeService.ALPHABET.charAt(random.nextInt(ReferralCodeService.ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normalize(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private static Specification<Promoter> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
