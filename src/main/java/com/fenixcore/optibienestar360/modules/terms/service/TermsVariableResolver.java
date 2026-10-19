package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.service.AllyFileNumbers;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the per-entity values of a T&C template ({@link TermsVariables})
 * for the concrete user it is served to. Every value is already formatted
 * for the contract text: dates {@code dd-MM-yyyy}, booleans {@code Sí}/{@code No}
 * (es-VE, through {@link DisplayFormatter}); a missing value is left out of
 * the map and renders as its pending note.
 *
 * <p>Call inside a transaction: the associations it reads (person, plan,
 * rank, ally) are lazy.</p>
 */
@Component
@RequiredArgsConstructor
public class TermsVariableResolver {

    /** Contracts are Spanish texts (ADR 0010): format their values in es-VE whatever the request locale. */
    static final Locale CONTRACT_LOCALE = Locale.forLanguageTag("es-VE");

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final DisplayFormatter displayFormatter;

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
                String.valueOf(beneficiaryRepository.countByMemberIdAndActiveTrue(member.getId())));
        membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId()).ifPresent(membership -> {
            put(values, "membership.planName", membership.getPlan() == null ? null : membership.getPlan().getName());
            put(values, "membership.activatedAt", date(membership.getEnrolledAt()));
        });
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
        put(values, "agreement.noticeDays", String.valueOf(agreement.getNoticeDays()));
        put(values, "agreement.autoRenew", displayFormatter.bool(agreement.isAutoRenew(), CONTRACT_LOCALE));
        put(values, "agreement.addendum", agreement.getTerms());
        return values;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String date(LocalDate value) {
        return displayFormatter.date(value, CONTRACT_LOCALE);
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
