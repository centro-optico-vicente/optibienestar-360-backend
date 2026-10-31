package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AllyFileNumbers;
import com.fenixcore.optibienestar360.modules.ally.service.OpeningHoursRules;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        String fullName = person == null ? null : person.getFullName();
        put(values, "advisor.code", advisor.getReferralCode());
        put(values, "advisor.fullName", fullName == null || fullName.isBlank() ? advisor.getDisplayName() : fullName);
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
        }
        put(values, "agreement.startDate", date(agreement.getStartDate()));
        put(values, "agreement.noticeDays", number(agreement.getNoticeDays()));
        put(values, "agreement.autoRenew", displayFormatter.bool(agreement.isAutoRenew(), CONTRACT_LOCALE));
        put(values, "agreement.addendum", agreement.getTerms());
        return new TermsRenderContext(values, Map.of(TermsVariables.AGREEMENT_RATES.key(), rates(ally)));
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
