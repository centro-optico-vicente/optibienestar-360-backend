package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AllyFileNumbers;
import com.fenixcore.optibienestar360.modules.ally.service.OpeningHoursRules;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.service.InscriptionPaymentMethodResolver;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense.VerificationStatus;
import com.fenixcore.optibienestar360.modules.person.repository.PersonProfessionalLicenseRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the per-entity values of a T&C template ({@link TermsVariables})
 * for the concrete user it is served to. Every value is already formatted
 * for the contract text: dates {@code dd-MM-yyyy}, booleans {@code Sí}/{@code No},
 * numbers with es-VE separators (all through {@link DisplayFormatter}); a
 * missing value is left out of the map and renders as its pending note.
 *
 * <p>Amounts ({@link TermsVariables.Kind#MONEY}) are written in their own
 * currency, never converted: a plan priced in US$ renders
 * {@code "US$ 25,00 o su equivalente en bolívares a la tasa oficial del BCV
 * vigente el día del pago"}. Freezing a converted figure would make the
 * contract wrong the day after; the payment is settled at that day's rate.
 * An amount already in the official currency gets no clause.</p>
 *
 * <p>Call inside a transaction: the associations it reads (person, plan,
 * rank, ally) are lazy.</p>
 */
@Component
@RequiredArgsConstructor
public class TermsVariableResolver {

    /** Contracts are Spanish texts (ADR 0010): format their values in es-VE whatever the request locale. */
    static final Locale CONTRACT_LOCALE = Locale.forLanguageTag("es-VE");

    /** Clause appended to an amount not in the official currency, when that currency is the bolívar (VES). */
    static final String MONEY_PAYMENT_CLAUSE_VES =
            "o su equivalente en bolívares a la tasa oficial del BCV vigente el día del pago";

    /** ISO code of the bolívar — the official currency the BCV clause is written for. */
    static final String VES = "VES";

    /** Rendered for {@code plan.maxBeneficiaries} when the plan has no upper bound ({@code NULL}, V13). */
    static final String UNLIMITED = "sin límite";

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final DisplayFormatter displayFormatter;
    private final OrganizationRepository organizationRepository;
    private final AllyProductRepository allyProductRepository;
    private final PersonProfessionalLicenseRepository licenseRepository;
    private final InscriptionPaymentMethodResolver inscriptionPaymentMethodResolver;

    /**
     * MEMBER values for {@code user}. The holder is the user's member record;
     * a user without one (e.g. staff) still gets name/document/birth date from
     * their own person, while the membership values stay pending.
     */
    public Map<String, String> forMember(User user) {
        Map<String, String> values = new HashMap<>();
        Member member = memberRepository.findByUserUuid(user.getUuid()).orElse(null);
        Person person = member != null ? member.getPerson() : user.getPerson();
        if (person != null) {
            put(values, "member.fullName", person.getFullName());
            put(values, "member.document", document(person.getDocumentType(), person.getDocumentNumber()));
            put(values, "member.birthDate", date(person.getBirthDate()));
        }
        if (member == null) {
            return values;
        }
        put(values, "beneficiaries.count",
                number(beneficiaryRepository.countByMemberIdAndActiveTrue(member.getId())));
        membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId()).ifPresent(membership -> {
            Plan plan = membership.getPlan();
            put(values, "membership.planName", plan == null ? null : plan.getName());
            put(values, "membership.activatedAt", date(membership.getEnrolledAt()));
            put(values, "membership.inscriptionPaymentMethod", inscriptionPaymentMethod(membership));
            if (plan != null) putPlan(values, plan);
        });
        return values;
    }

    /** The {@code plan.*} fees and beneficiary limits of the member's current plan. */
    private void putPlan(Map<String, String> values, Plan plan) {
        Currency currency = plan.getCurrency();
        Currency official = officialCurrency();
        put(values, "plan.monthlyFee", money(plan.getMonthlyFee(), currency, official));
        put(values, "plan.inscriptionFee", money(plan.getInscriptionFee(), currency, official));
        put(values, "plan.extraBeneficiaryInscriptionFee",
                money(plan.getExtraBeneficiaryInscriptionFee(), currency, official));
        put(values, "plan.includedBeneficiaries", number(plan.getIncludedBeneficiaries()));
        put(values, "plan.maxBeneficiaries",
                plan.getMaxBeneficiaries() == null ? UNLIMITED : number(plan.getMaxBeneficiaries()));
        put(values, "membership.activationFeeLabel", activationFeeLabel(plan, official));
    }

    /**
     * "Modalidad de pago" of the B2C dossier (hub ADR 0031 §14): the payment
     * method of the first approved inscription payment; {@code null}
     * (pending note) until there is one.
     */
    private String inscriptionPaymentMethod(Membership membership) {
        DisplayRef method = inscriptionPaymentMethodResolver.resolve(membership);
        if (method == null) return null;
        return method.name() == null || method.name().isBlank() ? method.code() : method.name();
    }

    /**
     * What activates the membership (Acuerdo B2C, QUINTA): the plan's
     * inscription plus the first monthly fee — the membership is annual,
     * paid monthly. {@code "inscripción de US$ 10,00 y primera mensualidad de
     * US$ 25,00"}, with the official-rate clause once at the end when the
     * plan is not priced in the official currency; just the first monthly
     * fee when the plan has no inscription fee. {@code null} without a
     * monthly fee.
     */
    String activationFeeLabel(Plan plan, Currency official) {
        String code = plan.getCurrency() == null ? null : plan.getCurrency().getCode();
        String monthly = plainMoney(plan.getMonthlyFee(), code);
        if (monthly == null) return null;
        BigDecimal inscription = plan.getInscriptionFee();
        String label = inscription == null || inscription.signum() == 0
                ? "primera mensualidad de " + monthly
                : "inscripción de " + plainMoney(inscription, code) + " y primera mensualidad de " + monthly;
        String officialCode = official == null ? VES : official.getCode();
        if (code == null || code.equalsIgnoreCase(officialCode)) {
            return label;
        }
        return label + ", " + paymentClause(official);
    }

    /**
     * MEMBER values for someone who is not a member yet — the public
     * affiliation form and its requests ({@code affiliation_requests}). Only
     * what the applicant typed: the holder and the chosen plan, with its
     * {@code plan.*} fees. The activation date and the beneficiaries stay
     * pending, they don't exist until the membership does.
     */
    public Map<String, String> forApplicant(MemberTermsApplicant applicant) {
        Map<String, String> values = new HashMap<>();
        put(values, "member.fullName", applicant.fullName());
        put(values, "member.document", document(applicant.documentType(), applicant.documentNumber()));
        put(values, "member.birthDate", date(applicant.birthDate()));
        if (applicant.plan() != null) {
            put(values, "membership.planName", applicant.plan().getName());
            putPlan(values, applicant.plan());
        }
        return values;
    }

    /** ADVISOR values from the advisor's own record ({@code promoters}). */
    public Map<String, String> forAdvisor(Promoter advisor) {
        Map<String, String> values = new HashMap<>();
        Person person = advisor.getPerson();
        put(values, "advisor.code", advisor.getReferralCode());
        put(values, "advisor.fullName", advisorName(advisor));
        put(values, "advisor.document",
                person == null ? null : document(person.getDocumentType(), person.getDocumentNumber()));
        put(values, "advisor.zone", advisor.getAssignedZone());
        PromoterRank rank = advisor.getRank();
        put(values, "advisor.rank", rank == null ? null : rank.getName());
        put(values, "advisor.joinedAt", date(advisor.getJoinedAt()));
        return values;
    }

    /**
     * ALLY values from the agreement that links the template and its ally,
     * plus the {@code agreement.rates} rows: one per approved, active service
     * of the ally (by name, case-insensitive), with the ally's segment
     * ({@code B2B}/{@code B2P}, both when its types mix them), the service
     * category, the service name, its discount (es-VE number, no {@code %})
     * and its operational exclusions.
     */
    public TermsRenderContext forAlly(AllyAgreement agreement) {
        Map<String, String> values = new HashMap<>();
        Ally ally = agreement.getAlly();
        if (ally != null) {
            put(values, "ally.name", ally.getName());
            put(values, "ally.taxId", document(ally.getTaxDocumentType(), ally.getTaxDocumentNumber()));
            put(values, "ally.representativeName", ally.getRepresentativeName());
            put(values, "ally.representativeDocument",
                    document(ally.getRepresentativeDocumentType(), ally.getRepresentativeDocumentNumber()));
            put(values, "ally.fileNumber", AllyFileNumbers.of(ally));
            put(values, "ally.openingHours",
                    OpeningHoursRules.label(OpeningHoursRules.of(ally.getOpeningHours()), CONTRACT_LOCALE));
            putAllyProfile(values, ally);
            putProfessional(values, ally);
        }
        put(values, "agreement.startDate", date(agreement.getStartDate()));
        put(values, "agreement.noticeDays", number(agreement.getNoticeDays()));
        put(values, "agreement.autoRenew", displayFormatter.bool(agreement.isAutoRenew(), CONTRACT_LOCALE));
        put(values, "agreement.addendum", agreement.getTerms());
        return new TermsRenderContext(values, Map.of(TermsVariables.AGREEMENT_RATES.key(), rates(ally)));
    }

    /**
     * The ally's own record as the B2B "Ficha Técnica" shows it (hub ADR 0031
     * §9): trade name, category (its types), address, city and state,
     * contact, operating structure, the signer's position and the advisor
     * who captured it.
     */
    private void putAllyProfile(Map<String, String> values, Ally ally) {
        put(values, "ally.tradeName", ally.getTradeName());
        put(values, "ally.typeNames", joinNames(ally.getAllyTypes().stream().map(AllyType::getName)));
        put(values, "ally.address", ally.getAddress());
        put(values, "ally.cityState", cityState(ally.getCity()));
        put(values, "ally.phone", ally.getPhone());
        put(values, "ally.socialLinks", Stream.of(ally.getWebsite(), ally.getInstagram(), ally.getFacebook())
                .filter(Objects::nonNull).map(String::trim).filter(link -> !link.isEmpty())
                .collect(Collectors.joining(" · ")));
        put(values, "ally.commercialZone", ally.getCommercialZone());
        // A year is not a quantity: no thousands separator ("2005", not "2.005").
        put(values, "ally.foundedYear", ally.getFoundedYear() == null ? null : String.valueOf(ally.getFoundedYear()));
        put(values, "ally.cashRegistersCount", number(ally.getCashRegistersCount()));
        put(values, "ally.branchesCount", number(ally.getBranchesCount()));
        put(values, "ally.hasPos", ally.getHasPos() == null ? null : displayFormatter.bool(ally.getHasPos(), CONTRACT_LOCALE));
        put(values, "ally.representativePosition", ally.getRepresentativePosition());
        Promoter advisor = ally.getCapturedByPromoter();
        if (advisor != null) {
            put(values, "ally.advisorCode", advisor.getReferralCode());
            put(values, "ally.advisorFullName", advisorName(advisor));
        }
    }

    /**
     * The {@code professional.*} values (Convenio Profesional, hub ADR 0031):
     * the ally's holder person ({@code allies.person_id}), the specialty and
     * sub-specialty from the ally's professions (a profession with a parent
     * is a sub-specialty, V213; its parent counts as the specialty), the
     * practice site and the licenses of the person.
     *
     * <p>Only <b>verified</b> licenses are written
     * ({@code verification_status = VERIFIED}): a contract must not certify a
     * number nobody checked. A pending or rejected license leaves its
     * variable pending ({@code [Nº M.P.P.S. pendiente]}).</p>
     */
    private void putProfessional(Map<String, String> values, Ally ally) {
        Person person = ally.getHolderPerson();
        if (person != null) {
            put(values, "professional.firstNames", joinWords(person.getFirstName(), person.getMiddleName()));
            put(values, "professional.lastNames", joinWords(person.getLastName(), person.getSecondLastName()));
            put(values, "professional.fullName", person.getFullName());
            put(values, "professional.document", document(person.getDocumentType(), person.getDocumentNumber()));
            put(values, "professional.birthDate", date(person.getBirthDate()));
            put(values, "professional.phone", person.getPhone());
            put(values, "professional.email", person.getEmail());
            putLicenses(values, person);
        }
        Set<String> specialties = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> subSpecialties = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Profession profession : ally.getProfessions()) {
            if (profession.getParent() == null) {
                addName(specialties, profession.getName());
            } else {
                addName(subSpecialties, profession.getName());
                addName(specialties, profession.getParent().getName());
            }
        }
        put(values, "professional.specialty", String.join(", ", specialties));
        put(values, "professional.subSpecialty", String.join(", ", subSpecialties));
        put(values, "professional.hostName", ally.getHostAlly() != null ? ally.getHostAlly().getName() : ally.getHostName());
        put(values, "professional.officeNumber", ally.getOfficeNumber());
        put(values, "professional.appointmentPhone", ally.getAppointmentPhone());
        put(values, "professional.assistantName", ally.getAssistantName());
    }

    /** {@code professional.licenses.<body code>} = license number(s) of the person, verified ones only. */
    private void putLicenses(Map<String, String> values, Person person) {
        if (person.getId() == null) return;
        List<PersonProfessionalLicense> licenses = licenseRepository.findByPerson_IdAndActiveTrueAndVerificationStatus(
                person.getId(), VerificationStatus.VERIFIED, Sort.by("id"));
        if (licenses == null || licenses.isEmpty()) return;
        Map<String, String> byBody = new LinkedHashMap<>();
        for (PersonProfessionalLicense license : licenses) {
            if (license.getLicensingBody() == null || license.getLicensingBody().getCode() == null) continue;
            String number = license.getLicenseNumber() == null ? "" : license.getLicenseNumber().trim();
            if (number.isEmpty()) continue;
            byBody.merge(TermsVariables.LICENSES_PREFIX + license.getLicensingBody().getCode(), number,
                    (a, b) -> a + ", " + b);
        }
        byBody.forEach((key, number) -> put(values, key, number));
    }

    /** {@code "Acarigua, Portuguesa"}; the city alone without a state; {@code null} without a city. */
    private static String cityState(City city) {
        if (city == null || city.getName() == null || city.getName().isBlank()) return null;
        String state = city.getState() == null ? null : city.getState().getName();
        return state == null || state.isBlank() ? city.getName().trim() : city.getName().trim() + ", " + state.trim();
    }

    /** Advisor's person name, or its display name when it has no person. */
    private static String advisorName(Promoter advisor) {
        Person person = advisor.getPerson();
        String fullName = person == null ? null : person.getFullName();
        return fullName == null || fullName.isBlank() ? advisor.getDisplayName() : fullName;
    }

    private static String joinNames(Stream<String> names) {
        Set<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.forEach(name -> addName(sorted, name));
        return String.join(", ", sorted);
    }

    private static void addName(Set<String> names, String name) {
        if (name != null && !name.isBlank()) names.add(name.trim());
    }

    private static String joinWords(String... parts) {
        return Stream.of(parts).filter(Objects::nonNull).map(String::trim).filter(p -> !p.isEmpty())
                .collect(Collectors.joining(" "));
    }

    /** The {@code agreement.rates} rows of {@code ally} (empty without a persisted ally). */
    private List<Map<String, String>> rates(Ally ally) {
        if (ally == null || ally.getId() == null) return List.of();
        List<AllyProduct> services = allyProductRepository.findByAllyIdAndReviewStatus(ally.getId(),
                ReviewStatus.APPROVED);
        if (services == null || services.isEmpty()) return List.of();
        String segment = segmentOf(ally);
        return services.stream()
                .filter(AllyProduct::isActive)
                .sorted(Comparator.comparing((AllyProduct s) -> s.getProduct().getName() == null ? "" : s.getProduct().getName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AllyProduct::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(service -> {
                    Map<String, String> row = new HashMap<>();
                    put(row, "segment", segment);
                    ProductCategory category = service.getProduct().getProductCategory();
                    put(row, "category", category == null ? null : category.getName());
                    // Placeholder kept as {{serviceName}}: stored agreement templates already use it (ADR 0026).
                    put(row, "serviceName", service.getProduct().getName());
                    // discountPct stays a plain percentage for templates written before V210:
                    // a FLAT discount shows its equivalent percentage there.
                    put(row, "discountPct", number(service.getEffectiveDiscountPct()));
                    put(row, "discountLabel", discountLabel(service));
                    String currencyCode = service.getPriceCurrency() == null ? null : service.getPriceCurrency().getCode();
                    put(row, "priceLabel", plainMoney(service.getPriceAmount(), currencyCode));
                    put(row, "finalPriceLabel", plainMoney(service.getFinalPrice(), currencyCode));
                    put(row, "exclusions", service.getExclusions());
                    return Map.copyOf(row);
                })
                .toList();
    }

    /** The ally's own segment ({@code allies.segment}, V211 / hub ADR 0031): {@code "B2B"} or {@code "B2P"}. */
    private static String segmentOf(Ally ally) {
        return ally.getSegment() == null ? null : ally.getSegment().name();
    }

    /**
     * The preferential rate as the agreement shows it: {@code "10 %"} for a
     * percentage, {@code "US$ 5,00"} for a fixed amount (hub ADR 0031 §8);
     * {@code null} (pending note) without a discount.
     */
    String discountLabel(AllyProduct offer) {
        if (offer.getDiscountType() == AllyProduct.DiscountType.FLAT) {
            String code = offer.getPriceCurrency() == null ? null : offer.getPriceCurrency().getCode();
            return plainMoney(offer.getDiscountAmount(), code);
        }
        String pct = number(offer.getDiscountPct());
        return pct == null ? null : pct + " %";
    }

    /**
     * A rates-table amount in its own currency, without the official-rate
     * clause of {@link #money}: the table repeats it on every row, and the
     * rates are commercial terms, not an amount the ally owes.
     */
    private String plainMoney(BigDecimal amount, String currencyCode) {
        return amount == null ? null : displayFormatter.money(amount, currencyCode, CONTRACT_LOCALE);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String date(LocalDate value) {
        return displayFormatter.date(value, CONTRACT_LOCALE);
    }

    private String number(Number value) {
        return displayFormatter.number(value, CONTRACT_LOCALE);
    }

    /**
     * {@code amount} in its own {@code currency} (es-VE), plus the payment
     * clause when that currency is not the {@code official} one ({@code null}
     * = VES). No converted figure is ever written (see the class javadoc).
     */
    String money(BigDecimal amount, Currency currency, Currency official) {
        if (amount == null) return null;
        String code = currency == null ? null : currency.getCode();
        String formatted = displayFormatter.money(amount, code, CONTRACT_LOCALE);
        String officialCode = official == null ? VES : official.getCode();
        if (code == null || code.equalsIgnoreCase(officialCode)) {
            return formatted;
        }
        return formatted + " " + paymentClause(official);
    }

    /**
     * The "pay at that day's official rate" clause. Fixed text for the
     * bolívar (the BCV publishes its rate); otherwise built from the official
     * currency's own name.
     */
    static String paymentClause(Currency official) {
        if (official == null || official.getCode() == null || VES.equalsIgnoreCase(official.getCode())) {
            return MONEY_PAYMENT_CLAUSE_VES;
        }
        String name = official.getName() == null || official.getName().isBlank()
                ? official.getCode() : official.getName().trim();
        return "o su equivalente en " + name + " a la tasa oficial vigente el día del pago";
    }

    /** The organization's official currency; {@code null} (treated as VES) when no organization is configured. */
    private Currency officialCurrency() {
        try {
            Organization organization = organizationRepository.findSingleton();
            return organization == null ? null : organization.getOfficialCurrency();
        } catch (IllegalStateException notConfigured) {
            return null;
        }
    }

    /** {@code "V-12345678"}; just the number when the type is missing; {@code null} without a number. */
    static String document(String type, String number) {
        if (number == null || number.isBlank()) return null;
        return type == null || type.isBlank() ? number.trim() : type.trim() + "-" + number.trim();
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }
}
