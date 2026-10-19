package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Per-type variable values built by {@link TermsVariableResolver} for the
 * concrete user, and how they render (formatted dd-MM-yyyy / Sí-No, missing
 * values as pending notes).
 */
@ExtendWith(MockitoExtension.class)
class TermsVariableResolverTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;

    private TermsVariableResolver resolver;

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        resolver = new TermsVariableResolver(memberRepository, membershipRepository, beneficiaryRepository,
                new DisplayFormatter(messageSource));
    }

    // ─── MEMBER ─────────────────────────────────────────────────────────────

    @Test
    void member_resolvesHolderMembershipAndBeneficiaries() {
        User user = user(person("María Pérez", "V", "12345678", LocalDate.of(1985, 3, 15)));
        Member member = new Member();
        member.setId(5L);
        member.setPerson(user.getPerson());
        Plan plan = new Plan();
        plan.setName("Plan Familiar");
        Membership membership = new Membership();
        membership.setPlan(plan);
        membership.setEnrolledAt(LocalDate.of(2026, 10, 1));
        when(memberRepository.findByUserUuid(user.getUuid())).thenReturn(Optional.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(5L)).thenReturn(Optional.of(membership));
        when(beneficiaryRepository.countByMemberIdAndActiveTrue(5L)).thenReturn(3L);

        Map<String, String> values = resolver.forMember(user);

        assertThat(values).containsEntry("member.fullName", "María Pérez")
                .containsEntry("member.document", "V-12345678")
                .containsEntry("member.birthDate", "15-03-1985")
                .containsEntry("membership.planName", "Plan Familiar")
                .containsEntry("membership.activatedAt", "01-10-2026")
                .containsEntry("beneficiaries.count", "3");
    }

    @Test
    void member_withoutMemberRecord_usesTheUsersPersonAndLeavesTheMembershipPending() {
        User user = user(person("Admin Uno", "V", "9999999", null));
        when(memberRepository.findByUserUuid(user.getUuid())).thenReturn(Optional.empty());

        Map<String, String> values = resolver.forMember(user);

        assertThat(values).containsOnlyKeys("member.fullName", "member.document");
        verifyNoInteractions(membershipRepository, beneficiaryRepository);
        assertThat(render("{{member.fullName}} · {{member.birthDate}} · {{membership.planName}} · {{beneficiaries.count}}", values))
                .isEqualTo("Admin Uno · [Fecha de nacimiento pendiente] · [Plan pendiente] · [Beneficiarios pendiente]");
    }

    @Test
    void member_withoutActiveMembership_rendersTheMembershipValuesAsPending() {
        User user = user(person("María Pérez", "V", "12345678", null));
        Member member = new Member();
        member.setId(5L);
        member.setPerson(user.getPerson());
        when(memberRepository.findByUserUuid(user.getUuid())).thenReturn(Optional.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(5L)).thenReturn(Optional.empty());
        when(beneficiaryRepository.countByMemberIdAndActiveTrue(5L)).thenReturn(0L);

        Map<String, String> values = resolver.forMember(user);

        assertThat(render("{{membership.activatedAt}} / {{beneficiaries.count}}", values))
                .isEqualTo("[Fecha de activación pendiente] / 0");
    }

    // ─── ADVISOR ────────────────────────────────────────────────────────────

    @Test
    void advisor_resolvesTheAdvisorRecord() {
        PromoterRank rank = new PromoterRank();
        rank.setCode("SUPERVISOR");
        rank.setName("Supervisor");
        Promoter advisor = new Promoter();
        advisor.setPerson(person("José Rodríguez", "V", "15678901", null));
        advisor.setDisplayName("José R.");
        advisor.setReferralCode("ASE001");
        advisor.setAssignedZone("Acarigua centro");
        advisor.setRank(rank);
        advisor.setJoinedAt(LocalDate.of(2026, 9, 1));

        Map<String, String> values = resolver.forAdvisor(advisor);

        assertThat(values).containsEntry("advisor.code", "ASE001")
                .containsEntry("advisor.fullName", "José Rodríguez")
                .containsEntry("advisor.document", "V-15678901")
                .containsEntry("advisor.zone", "Acarigua centro")
                .containsEntry("advisor.rank", "Supervisor")
                .containsEntry("advisor.joinedAt", "01-09-2026");
    }

    @Test
    void advisor_withoutPersonOrOptionalData_fallsBackToDisplayNameAndPendingNotes() {
        Promoter advisor = new Promoter();
        advisor.setDisplayName("Asesor Sin Ficha");
        advisor.setReferralCode("ASE002");

        Map<String, String> values = resolver.forAdvisor(advisor);

        assertThat(render("{{advisor.fullName}} · {{advisor.document}} · {{advisor.zone}} · {{advisor.rank}} · {{advisor.joinedAt}}",
                values)).isEqualTo("Asesor Sin Ficha · [Cédula del asesor pendiente] · [Zona asignada pendiente] · "
                + "[Rango pendiente] · [Fecha de ingreso pendiente]");
    }

    // ─── ALLY ───────────────────────────────────────────────────────────────

    @Test
    void ally_resolvesTheAllyAndTheLinkingAgreement() {
        Promoter capturer = new Promoter();
        capturer.setReferralCode("ASE001");
        Ally ally = new Ally();
        ally.setName("Clínica Ejemplo, C.A.");
        ally.setTaxDocumentType("J");
        ally.setTaxDocumentNumber("12345678-9");
        ally.setRepresentativeName("Ana López");
        ally.setRepresentativeDocumentType("V");
        ally.setRepresentativeDocumentNumber("9876543");
        ally.setAllyNumber(7);
        ally.setCapturedByPromoter(capturer);
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);
        agreement.setStartDate(LocalDate.of(2026, 10, 1));
        agreement.setNoticeDays(45);
        agreement.setAutoRenew(false);
        agreement.setTerms("Descuento adicional los sábados.");

        Map<String, String> values = resolver.forAlly(agreement);

        assertThat(values).containsEntry("ally.name", "Clínica Ejemplo, C.A.")
                .containsEntry("ally.taxId", "J-12345678-9")
                .containsEntry("ally.representativeName", "Ana López")
                .containsEntry("ally.representativeDocument", "V-9876543")
                .containsEntry("ally.fileNumber", "ASE001-0007")
                .containsEntry("agreement.startDate", "01-10-2026")
                .containsEntry("agreement.noticeDays", "45")
                .containsEntry("agreement.autoRenew", "No")
                .containsEntry("agreement.addendum", "Descuento adicional los sábados.");
    }

    @Test
    void ally_autoRenewIsSpanishAndMissingDataRendersPending() {
        Ally ally = new Ally();
        ally.setName("Óptica Uno");
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);
        agreement.setAutoRenew(true);

        Map<String, String> values = resolver.forAlly(agreement);

        assertThat(render("{{agreement.autoRenew}} · {{ally.taxId}} · {{ally.representativeName}} · {{agreement.addendum}}",
                values)).isEqualTo("Sí · [RIF del aliado pendiente] · [Representante pendiente] · "
                + "[Condiciones particulares pendiente]");
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private static String render(String template, Map<String, String> values) {
        return TermsTemplateRenderer.render(template, new Organization(), values);
    }

    private static User user(Person person) {
        User user = new User();
        user.setId(1L);
        user.setUuid(UUID.randomUUID());
        user.setPerson(person);
        return user;
    }

    private static Person person(String fullName, String documentType, String documentNumber, LocalDate birthDate) {
        Person person = new Person();
        person.setFullName(fullName);
        person.setDocumentType(documentType);
        person.setDocumentNumber(documentNumber);
        person.setBirthDate(birthDate);
        return person;
    }
}
