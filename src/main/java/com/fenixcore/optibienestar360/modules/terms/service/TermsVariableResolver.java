package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.service.AllyFileNumbers;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
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
import java.util.HashMap;
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

    /** ALLY values from the agreement that links the template and its ally. */
    public Map<String, String> forAlly(AllyAgreement agreement) {
        Map<String, String> values = new HashMap<>();
        Ally ally = agreement.getAlly();
        if (ally != null) {
            put(values, "ally.name", ally.getName());
            put(values, "ally.taxId", document(ally.getTaxDocumentType(), ally.getTaxDocumentNumber()));
            put(values, "ally.representativeName", ally.getRepresentativeName());
            put(values, "ally.representativeDocument",
                    document(ally.getRepresentativeDocumentType(), ally.getRepresentativeDocumentNumber()));
            put(values, "ally.fileNumber", AllyFileNumbers.of(ally));
        }
        put(values, "agreement.startDate", date(agreement.getStartDate()));
        put(values, "agreement.noticeDays", number(agreement.getNoticeDays()));
        put(values, "agreement.autoRenew", displayFormatter.bool(agreement.isAutoRenew(), CONTRACT_LOCALE));
        put(values, "agreement.addendum", agreement.getTerms());
        return values;
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
