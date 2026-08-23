package com.fenixcore.optibienestar360.core.audit;

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

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

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
 */
@Slf4j
@Component
public class AuditDisplayResolver {

    /** JVM-wide default set in {@code OptiBienestar360Application} — Venezuela is the only deployment (ADR 0010). */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Caracas");

    private static final DateTimeFormatter DATE_TIME_ES = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter DATE_TIME_EN = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY_ES = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_ONLY_EN = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /** UPPER_SNAKE_CASE token, e.g. {@code PENDING}, {@code IN_REVIEW} — how every enum-backed
     *  field in this codebase serializes at the generic {@code Map<String,Object>} snapshot level. */
    private static final Pattern ENUM_SHAPED = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    private final Map<String, Function<UUID, Optional<String>>> entityResolvers = new LinkedHashMap<>();
    private final Map<String, Function<UUID, Optional<String>>> fieldResolvers = new LinkedHashMap<>();
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    public AuditDisplayResolver(
            MessageSource messageSource,
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
        this.userRepository = userRepository;

        // entity_key -> this specific record's own label.
        entityResolvers.put("ally", uuid -> allyRepository.findByUuid(uuid).map(a -> a.getName()));
        entityResolvers.put("ally_type", uuid -> allyTypeRepository.findByUuid(uuid).map(a -> a.getCode() + " - " + a.getName()));
        entityResolvers.put("member", uuid -> memberRepository.findByUuid(uuid).map(m -> m.getPerson().getFullName()));
        entityResolvers.put("beneficiary", uuid -> beneficiaryRepository.findByUuid(uuid).map(b -> b.getPerson().getFullName()));
        // medical_record / member_document / member_promoter are keyed by the owning member's uuid (see spec's
        // per-entity uuidArgIndex notes), so the member's name is the meaningful label for all three.
        entityResolvers.put("medical_record", uuid -> memberRepository.findByUuid(uuid).map(m -> m.getPerson().getFullName()));
        entityResolvers.put("member_document", uuid -> memberRepository.findByUuid(uuid).map(m -> m.getPerson().getFullName()));
        entityResolvers.put("member_promoter", uuid -> memberRepository.findByUuid(uuid).map(m -> m.getPerson().getFullName()));
        entityResolvers.put("plan", uuid -> planRepository.findByUuid(uuid).map(p -> p.getName()));
        entityResolvers.put("membership", uuid -> membershipRepository.findByUuid(uuid)
                .map(ms -> ms.getMember().getPerson().getFullName() + " — " + ms.getPlan().getName()));
        entityResolvers.put("promoter", uuid -> promoterRepository.findByUuid(uuid).map(p -> p.getDisplayName()));
        entityResolvers.put("commission_tier", uuid -> commissionTierRepository.findByUuid(uuid).map(t -> t.getName()));
        entityResolvers.put("corporate_contract", uuid -> corporateContractRepository.findByUuid(uuid).map(c -> c.getInstitutionName()));
        entityResolvers.put("role", uuid -> roleRepository.findByUuid(uuid).map(r -> r.getName()));
        entityResolvers.put("user", uuid -> userRepository.findByUuid(uuid).map(u -> u.getPerson().getFullName()));
        entityResolvers.put("scheduled_job", uuid -> scheduledJobRepository.findByUuid(uuid).map(j -> j.getDisplayName()));

        // changed-field JSON key -> the referenced row's label (added as "<field>Display").
        fieldResolvers.put("cityUuid", uuid -> cityRepository.findByUuid(uuid).map(c -> c.getName()));
        fieldResolvers.put("countryUuid", uuid -> countryRepository.findByUuid(uuid).map(c -> c.getName()));
        fieldResolvers.put("genderUuid", uuid -> genderRepository.findByUuid(uuid).map(g -> g.getCode() + " - " + g.getName()));
        fieldResolvers.put("maritalStatusUuid", uuid -> maritalStatusRepository.findByUuid(uuid).map(m -> m.getCode() + " - " + m.getName()));
        fieldResolvers.put("occupationUuid", uuid -> occupationRepository.findByUuid(uuid).map(o -> o.getName()));
        fieldResolvers.put("medicalSpecialtyUuid", uuid -> medicalSpecialtyRepository.findByUuid(uuid).map(s -> s.getCode() + " - " + s.getName()));
        fieldResolvers.put("serviceCategoryUuid", uuid -> serviceCategoryRepository.findByUuid(uuid).map(c -> c.getCode() + " - " + c.getName()));
        fieldResolvers.put("allyTypeUuid", uuid -> allyTypeRepository.findByUuid(uuid).map(a -> a.getCode() + " - " + a.getName()));
        fieldResolvers.put("promoterTypeUuid", uuid -> promoterTypeRepository.findByUuid(uuid).map(t -> t.getCode() + " - " + t.getName()));
        fieldResolvers.put("planUuid", uuid -> planRepository.findByUuid(uuid).map(p -> p.getName()));
        fieldResolvers.put("memberUuid", uuid -> memberRepository.findByUuid(uuid).map(m -> m.getPerson().getFullName()));
        fieldResolvers.put("promoterUuid", uuid -> promoterRepository.findByUuid(uuid).map(p -> p.getDisplayName()));
        fieldResolvers.put("allyUuid", uuid -> allyRepository.findByUuid(uuid).map(a -> a.getName()));
        fieldResolvers.put("userUuid", uuid -> userRepository.findByUuid(uuid).map(u -> u.getPerson().getFullName()));
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
            return userRepository.findById(actorId).map(u -> u.getPerson().getFullName());
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
     *   <li>{@link Boolean} → localized "Sí/No" / "Yes/No".</li>
     *   <li>{@link String} that parses as a {@link UUID} → the FK-field
     *       registry ({@link #fieldResolvers}), e.g. {@code cityUuid} → the
     *       city's name.</li>
     *   <li>{@link String} that parses as an ISO-8601 instant or date → the
     *       locale/country date format (Venezuela: {@code dd-MM-yyyy HH:mm},
     *       ADR 0010), in {@link #DISPLAY_ZONE}.</li>
     *   <li>{@link Number} → locale-aware thousands/decimal separators,
     *       preserving the value's own precision (whole numbers get no
     *       decimals; fractional ones keep their significant digits).</li>
     *   <li>{@link String} shaped like an {@code UPPER_SNAKE_CASE} enum
     *       constant → translated via {@code audit.enum.<field>.<value>},
     *       falling back to the shared {@code audit.enum.common.<value>}
     *       vocabulary — silently skipped (no {@code _Display}) if neither
     *       key exists, rather than showing a fake translation.</li>
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
            return Optional.of(messageSource.getMessage(
                    "audit.value." + b, null, b ? "Yes" : "No", locale));
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
            return tryEnumDisplay(key, s, locale);
        }
        if (value instanceof Number n) {
            return Optional.of(formatNumber(n, locale));
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
        DateTimeFormatter dateTimePattern = "es".equals(locale.getLanguage()) ? DATE_TIME_ES : DATE_TIME_EN;
        DateTimeFormatter datePattern = "es".equals(locale.getLanguage()) ? DATE_ONLY_ES : DATE_ONLY_EN;
        try {
            return Optional.of(Instant.parse(value).atZone(DISPLAY_ZONE).format(dateTimePattern));
        } catch (DateTimeParseException notAnInstant) {
            try {
                return Optional.of(LocalDate.parse(value).format(datePattern));
            } catch (DateTimeException notADate) {
                return Optional.empty();
            }
        }
    }

    private Optional<String> tryEnumDisplay(String key, String value, Locale locale) {
		// "code" is always a natural key (UPPER_SNAKE_CASE by convention), never an enum
		// value — even when it happens to collide with an unrelated enum constant (e.g. a
		// plan's code "CORPORATIVO" matching PlanType.CORPORATIVO).
		if ("code".equals(key)) {
			return Optional.empty();
		}
        if (!ENUM_SHAPED.matcher(value).matches()) {
            return Optional.empty();
        }
        String scoped = messageSource.getMessage("audit.enum." + key + "." + value, null, null, locale);
        if (scoped != null) {
            return Optional.of(scoped);
        }
        String common = messageSource.getMessage("audit.enum.common." + value, null, null, locale);
        return Optional.ofNullable(common);
    }

    /**
     * Whole numbers get thousands separators only ({@code 1.234} es /
     * {@code 1,234} en); anything with a fractional part keeps its own
     * significant decimals with the locale's separator ({@code 12,5} es /
     * {@code 12.5} en) — precision isn't hardcoded to 2, it reflects what the
     * value actually carries (a {@code BigDecimal}'s scale, or a double's own
     * digits), so {@code 12.50} shows as {@code 12,50} and {@code 12.5} as
     * {@code 12,5}.
     */
    private String formatNumber(Number n, Locale locale) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short) {
            return NumberFormat.getIntegerInstance(locale).format(n.longValue());
        }
        BigDecimal decimal = n instanceof BigDecimal bd ? bd : BigDecimal.valueOf(n.doubleValue());
        if (decimal.stripTrailingZeros().scale() <= 0) {
            return NumberFormat.getIntegerInstance(locale).format(decimal.longValue());
        }
        int scale = Math.max(decimal.scale(), 0);
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(scale);
        format.setMaximumFractionDigits(scale);
        return format.format(decimal);
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
