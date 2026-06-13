package com.fenixcore.optisaludplus.modules.promoter.service;

import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.person.repository.PersonRepository;
import com.fenixcore.optisaludplus.modules.promoter.dto.PromoterCreateRequest;
import com.fenixcore.optisaludplus.modules.promoter.dto.PromoterDto;
import com.fenixcore.optisaludplus.modules.promoter.dto.PromoterUpdateRequest;
import com.fenixcore.optisaludplus.modules.promoter.entity.Promoter;
import com.fenixcore.optisaludplus.modules.promoter.entity.Promoter.PromoterStatus;
import com.fenixcore.optisaludplus.modules.promoter.mapper.PromoterMapper;
import com.fenixcore.optisaludplus.modules.promoter.repository.PromoterRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PromoterRepository repository;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final PromoterMapper mapper;

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
        if (repository.existsByReferralCode(req.referralCode())) {
            throw new IllegalArgumentException("promoter.referral_code.duplicate");
        }

        User user = userRepository.findByUuid(req.userUuid())
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        Person person = personRepository.findByUuid(req.personUuid())
                .orElseThrow(() -> new NoSuchElementException("person.not_found"));

        // Reject if the user is already a promoter — keeps the partial
        // UNIQUE (user_id WHERE is_active) free for a clean re-create flow.
        if (repository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("promoter.user.already_assigned");
        }

        Promoter promoter = new Promoter();
        promoter.setDisplayName(req.displayName());
        promoter.setDescription(req.description());
        promoter.setReferralCode(req.referralCode());
        promoter.setSystem(false);  // API never creates system rows
        promoter.setUser(user);
        promoter.setPerson(person);
        promoter.setEmail(req.email());
        promoter.setPhone(req.phone());
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

    private static void ensureNotSystemRow(Promoter promoter) {
        if (promoter.isSystem()) {
            throw new IllegalArgumentException("promoter.system.not_editable");
        }
    }

    private static Specification<Promoter> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
