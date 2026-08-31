package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.CityRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.CountryRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.GenderRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.MaritalStatusRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.MedicalSpecialtyRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.OccupationRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.ServiceCategoryRepository;
import com.fenixcore.optibienestar360.modules.corporate.repository.CorporateContractRepository;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves human-readable display values for the audit read endpoints —
 * "a UUID/BIGINT/timestamp-heavy history is not intuitive" is exactly what
 * this exists to fix (spec 16-audit.md §Endpoints admin). Two registries:
 *
 * <ul>
 *   <li>{@code entityKey} → the changed record's own label (e.g. an
 *       {@code ally}'s {@code name}), used for {@code entity_display}.</li>
 *   <li>a changed-field's JSON key (e.g. {@code cityUuid}) → the referenced
 *       row's label, used to add a {@code <field>Display} sibling next to
 *       every FK-shaped UUID inside a before/after snapshot.</li>
 * </ul>
 *
 * <p>Both registries are intentionally partial — covering the 22 entities
 * already wired to {@code @Auditable} plus their common catalog FKs is
 * "good enough" now; an unrecognized {@code entityKey}/field resolves to
 * {@link Optional#empty()} rather than failing, so the raw uuid still shows
 * up in the response (degraded, not broken). Extend the registries as new
 * entities/fields come up in the UI.</p>
 *
 * <p><b>Formatting</b> (dates, numbers, booleans, enums, catalog/person
 * labels) is delegated to {@link DisplayFormatter} — the same authority the
 * {@code _Display} convention (hub ADR 0014) uses everywhere else, so a
 * value looks identical in a list, a detail view and an audit snapshot. This
 * class keeps only the *resolution* registries (JSON key/entity key →
 * repository → entity).</p>
 */
@Slf4j
@Component
public class AuditDisplayResolver {

    private final Map<String, Function<UUID, Optional<String>>> entityResolvers = new LinkedHashMap<>();
    private final Map<String, Function<UUID, Optional<String>>> fieldResolvers = new LinkedHashMap<>();
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final DisplayFormatter displayFormatter;

    public AuditDisplayResolver(
            MessageSource messageSource,
			DisplayFormatter displayFormatter,
            UserRepository userRepository,
            AllyRepository allyRepository,
            MemberRepository memberRepository,
            PlanRepository planRepository,
            PromoterRepository promoterRepository,
            RoleRepository roleRepository,
            ScheduledJobRepository scheduledJobRepository,
            AllyTypeRepository allyTypeRepository,
            CorporateContractRepository corporateContractRepository,
            MembershipRepository membershipRepository,
            BeneficiaryRepository beneficiaryRepository,
            CommissionTierRepository commissionTierRepository,
            CityRepository cityRepository,
            CountryRepository countryRepository,
            GenderRepository genderRepository,
            MaritalStatusRepository maritalStatusRepository,
            OccupationRepository occupationRepository,
            MedicalSpecialtyRepository medicalSpecialtyRepository,
            ServiceCategoryRepository serviceCategoryRepository,
            PromoterTypeRepository promoterTypeRepository) {
        this.messageSource = messageSource;
        this.displayFormatter = displayFormatter;
        this.userRepository = userRepository;

        // entity_key -> this specific record's own label.
        entityResolvers.put("ally", uuid -> allyRepository.findByUuid(uuid).map(a -> a.getName()));
		entityResolvers.put("ally_type", uuid -> allyTypeRepository.findByUuid(uuid)
				.map(a -> displayFormatter.catalogLabel(a.getCode(), a.getName())));
		entityResolvers.put("member", uuid -> memberRepository.findByUuid(uuid)
				.map(m -> displayFormatter.personLabel(m.getPerson().getTaxDocumentNumber(), m.getPerson().getFullName())));
		entityResolvers.put("beneficiary", uuid -> beneficiaryRepository.findByUuid(uuid)
				.map(b -> displayFormatter.personLabel(b.getPerson().getTaxDocumentNumber(), b.getPerson().getFullName())));
        // medical_record / member_document / member_promoter are keyed by the owning member's uuid (see spec's
        // per-entity uuidArgIndex notes), so the member's name is the meaningful label for all three.
		entityResolvers.put("medical_record", uuid -> memberRepository.findByUuid(uuid)
				.map(m -> displayFormatter.personLabel(m.getPerson().getTaxDocumentNumber(), m.getPerson().getFullName())));
		entityResolvers.put("member_document", uuid -> memberRepository.findByUuid(uuid)
				.map(m -> displayFormatter.personLabel(m.getPerson().getTaxDocumentNumber(), m.getPerson().getFullName())));
		entityResolvers.put("member_promoter", uuid -> memberRepository.findByUuid(uuid)
				.map(m -> displayFormatter.personLabel(m.getPerson().getTaxDocumentNumber(), m.getPerson().getFullName())));
        entityResolvers.put("plan", uuid -> planRepository.findByUuid(uuid).map(p -> p.getName()));
        entityResolvers.put("membership", uuid -> membershipRepository.findByUuid(uuid)
                .map(ms -> ms.getMember().getPerson().getFullName() + " — " + ms.getPlan().getName()));
        entityResolvers.put("promoter", uuid -> promoterRepository.findByUuid(uuid).map(p -> p.getDisplayName()));
        entityResolvers.put("commission_tier", uuid -> commissionTierRepository.findByUuid(uuid).map(t -> t.getName()));
        entityResolvers.put("corporate_contract", uuid -> corporateContractRepository.findByUuid(uuid).map(c -> c.getInstitutionName()));
        entityResolvers.put("role", uuid -> roleRepository.findByUuid(uuid).map(r -> r.getName()));
		entityResolvers.put("user", uuid -> userRepository.findByUuid(uuid)
				.map(u -> displayFormatter.personLabel(u.getPerson().getTaxDocumentNumber(), u.getPerson().getFullName())));
        entityResolvers.put("scheduled_job", uuid -> scheduledJobRepository.findByUuid(uuid).map(j -> j.getDisplayName()));

        // changed-field JSON key -> the referenced row's label (added as "<field>Display").
        fieldResolvers.put("cityUuid", uuid -> cityRepository.findByUuid(uuid).map(c -> c.getName()));
        fieldResolvers.put("countryUuid", uuid -> countryRepository.findByUuid(uuid).map(c -> c.getName()));
		fieldResolvers.put("genderUuid", uuid -> genderRepository.findByUuid(uuid)
				.map(g -> displayFormatter.catalogLabel(g.getCode(), g.getName())));
		fieldResolvers.put("maritalStatusUuid", uuid -> maritalStatusRepository.findByUuid(uuid)
				.map(m -> displayFormatter.catalogLabel(m.getCode(), m.getName())));
        fieldResolvers.put("occupationUuid", uuid -> occupationRepository.findByUuid(uuid).map(o -> o.getName()));
		fieldResolvers.put("medicalSpecialtyUuid", uuid -> medicalSpecialtyRepository.findByUuid(uuid)
				.map(s -> displayFormatter.catalogLabel(s.getCode(), s.getName())));
		fieldResolvers.put("serviceCategoryUuid", uuid -> serviceCategoryRepository.findByUuid(uuid)
				.map(c -> displayFormatter.catalogLabel(c.getCode(), c.getName())));
		fieldResolvers.put("allyTypeUuid", uuid -> allyTypeRepository.findByUuid(uuid)
				.map(a -> displayFormatter.catalogLabel(a.getCode(), a.getName())));
		fieldResolvers.put("promoterTypeUuid", uuid -> promoterTypeRepository.findByUuid(uuid)
				.map(t -> displayFormatter.catalogLabel(t.getCode(), t.getName())));
        fieldResolvers.put("planUuid", uuid -> planRepository.findByUuid(uuid).map(p -> p.getName()));
		fieldResolvers.put("memberUuid", uuid -> memberRepository.findByUuid(uuid)
				.map(m -> displayFormatter.personLabel(m.getPerson().getTaxDocumentNumber(), m.getPerson().getFullName())));
        fieldResolvers.put("promoterUuid", uuid -> promoterRepository.findByUuid(uuid).map(p -> p.getDisplayName()));
        fieldResolvers.put("allyUuid", uuid -> allyRepository.findByUuid(uuid).map(a -> a.getName()));
		fieldResolvers.put("userUuid", uuid -> userRepository.findByUuid(uuid)
				.map(u -> displayFormatter.personLabel(u.getPerson().getTaxDocumentNumber(), u.getPerson().getFullName())));
        fieldResolvers.put("roleUuid", uuid -> roleRepository.findByUuid(uuid).map(r -> r.getName()));
        fieldResolvers.put("defaultRoleUuid", uuid -> roleRepository.findByUuid(uuid).map(r -> r.getName()));
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveEntityDisplay(String entityKey, UUID entityUuid) {
        return resolveSafely(entityResolvers.get(entityKey), entityUuid);
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveActorName(Long actorId) {
        if (actorId == null) {
            return Optional.empty();
        }
        try {
			return userRepository.findById(actorId)
				.map(u -> displayFormatter.personLabel(u.getPerson().getTaxDocumentNumber(), u.getPerson().getFullName()))
			;
        } catch (Exception e) {
            log.debug("Could not resolve actor name for actorId={}", actorId, e);
            return Optional.empty();
        }
    }

    public String resolveActionLabel(AuditAction action, Locale locale) {
        String code = "audit.action." + action.name();
        return messageSource.getMessage(code, null, action.name(), locale);
    }

    /**
     * Returns a shallow copy of {@code snapshot} with a {@code <field>_Display}
     * sibling added next to every value this can humanize — the original
     * key/value is always left untouched (so the frontend keeps the raw uuid,
     * boolean, number, ISO date, or enum token for logic/links). Dispatch by
     * value shape, in order:
     *
     * <ol>
     *   <li>{@link Boolean} → localized "Sí/No" / "Yes/No" ({@link DisplayFormatter#bool}).</li>
     *   <li>{@link String} that parses as a {@link UUID} → the FK-field
     *       registry ({@link #fieldResolvers}), e.g. {@code cityUuid} → the
     *       city's name.</li>
     *   <li>{@link String} that parses as an ISO-8601 instant or date → the
     *       locale/country date format ({@link DisplayFormatter#dateTime}/
     *       {@link DisplayFormatter#date}, ADR 0010).</li>
     *   <li>{@link Number} → locale-aware thousands/decimal separators
     *       ({@link DisplayFormatter#number}).</li>
     *   <li>{@link String} shaped like an {@code UPPER_SNAKE_CASE} enum
     *       constant → translated via {@link DisplayFormatter#enumLabel} —
     *       silently skipped (no {@code _Display}) if no key exists, rather
     *       than showing a fake translation.</li>
     *   <li>Anything else (free-text strings) → no {@code _Display}; already
     *       human-readable.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> withFieldDisplays(Map<String, Object> snapshot, Locale locale) {
        if (snapshot == null || snapshot.isEmpty()) {
            return snapshot;
        }
        Map<String, Object> result = new LinkedHashMap<>(snapshot);
        snapshot.forEach((key, value) -> displayFor(key, value, locale).ifPresent(display -> result.put(key + "_Display", display)));
        return result;
    }

    private Optional<String> displayFor(String key, Object value, Locale locale) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Boolean b) {
            return Optional.of(displayFormatter.bool(b, locale));
        }
        if (value instanceof String s) {
            Optional<String> uuidDisplay = tryFkDisplay(key, s);
            if (uuidDisplay.isPresent()) {
                return uuidDisplay;
            }
            Optional<String> dateDisplay = tryDateDisplay(s, locale);
            if (dateDisplay.isPresent()) {
                return dateDisplay;
            }
            return Optional.ofNullable(displayFormatter.enumLabel(key, s, locale));
        }
        if (value instanceof Number n) {
            return Optional.ofNullable(displayFormatter.number(n, locale));
        }
        return Optional.empty();
    }

    private Optional<String> tryFkDisplay(String key, String value) {
        Function<UUID, Optional<String>> resolver = fieldResolvers.get(key);
        if (resolver == null) {
            return Optional.empty();
        }
        try {
            return resolveSafely(resolver, UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private Optional<String> tryDateDisplay(String value, Locale locale) {
        try {
            return Optional.ofNullable(displayFormatter.dateTime(Instant.parse(value), locale));
        } catch (DateTimeParseException notAnInstant) {
            try {
                return Optional.ofNullable(displayFormatter.date(LocalDate.parse(value), locale));
            } catch (DateTimeException notADate) {
                return Optional.empty();
            }
        }
    }

    private Optional<String> resolveSafely(Function<UUID, Optional<String>> resolver, UUID uuid) {
        if (resolver == null || uuid == null) {
            return Optional.empty();
        }
        try {
            return resolver.apply(uuid);
        } catch (Exception e) {
            log.debug("Display resolution failed for uuid={}", uuid, e);
            return Optional.empty();
        }
    }

}
