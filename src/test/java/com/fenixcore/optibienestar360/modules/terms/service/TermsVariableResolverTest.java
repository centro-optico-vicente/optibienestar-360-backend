package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Per-type variable values built by {@link TermsVariableResolver} for the
 * concrete user, and how they render (formatted dd-MM-yyyy / Sí-No, es-VE
 * numbers, amounts in their own currency with the BCV clause, missing values
 * as pending notes).
 */
@ExtendWith(MockitoExtension.class)
class TermsVariableResolverTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private AllyProductRepository allyProductRepository;

    private TermsVariableResolver resolver;
    private DisplayFormatter formatter;

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        formatter = new DisplayFormatter(messageSource);
        resolver = new TermsVariableResolver(memberRepository, membershipRepository, beneficiaryRepository,
                formatter, organizationRepository, allyProductRepository);
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

    // ─── MEMBER: plan amounts and numbers ───────────────────────────────────

    @Test
    void member_planAmountInUsd_isWrittenInUsdWithTheBcvClauseAndNoConvertedFigure() {
        officialCurrencyIs(currency("VES", "Bolívar venezolano"));
        Plan plan = plan(currency("USD", "Dólar estadounidense"));
        plan.setMonthlyFee(new BigDecimal("25.00"));
        plan.setInscriptionFee(new BigDecimal("10.00"));
        plan.setExtraBeneficiaryInscriptionFee(new BigDecimal("5.00"));
        plan.setIncludedBeneficiaries(3);
        plan.setMaxBeneficiaries(12000);

        Map<String, String> values = resolver.forMember(memberWithPlan(plan));

        String clause = " o su equivalente en bolívares a la tasa oficial del BCV vigente el día del pago";
        assertThat(values.get("plan.monthlyFee"))
                .isEqualTo(formatter.money(new BigDecimal("25.00"), "USD", TermsVariableResolver.CONTRACT_LOCALE) + clause)
                .contains("25,00");
        assertThat(values.get("plan.inscriptionFee")).contains("10,00").endsWith(clause);
        assertThat(values.get("plan.extraBeneficiaryInscriptionFee")).contains("5,00").endsWith(clause);
        assertThat(values).containsEntry("plan.includedBeneficiaries", "3")
                .containsEntry("plan.maxBeneficiaries", "12.000");
    }

    @Test
    void member_planAmountInTheOfficialCurrency_hasNoClause() {
        Currency ves = currency("VES", "Bolívar venezolano");
        officialCurrencyIs(ves);
        Plan plan = plan(ves);
        plan.setMonthlyFee(new BigDecimal("900.00"));
        plan.setInscriptionFee(new BigDecimal("15000.50"));

        Map<String, String> values = resolver.forMember(memberWithPlan(plan));

        assertThat(values.get("plan.monthlyFee"))
                .isEqualTo(formatter.money(new BigDecimal("900.00"), "VES", TermsVariableResolver.CONTRACT_LOCALE))
                .doesNotContain("equivalente");
        assertThat(values.get("plan.inscriptionFee")).contains("15.000,50");
        assertThat(values).doesNotContainKey("plan.extraBeneficiaryInscriptionFee")
                .containsEntry("plan.maxBeneficiaries", "sin límite");
    }

    @Test
    void member_officialCurrencyOtherThanTheBolivar_namesItInTheClause() {
        officialCurrencyIs(currency("EUR", "Euro"));
        Plan plan = plan(currency("USD", "Dólar estadounidense"));
        plan.setMonthlyFee(new BigDecimal("25.00"));
        plan.setInscriptionFee(new BigDecimal("0.00"));

        Map<String, String> values = resolver.forMember(memberWithPlan(plan));

        assertThat(values.get("plan.monthlyFee"))
                .endsWith(" o su equivalente en Euro a la tasa oficial vigente el día del pago");
    }

    @Test
    void member_withoutPlan_rendersThePlanAmountsAsPending() {
        User user = user(person("María Pérez", "V", "12345678", null));
        when(memberRepository.findByUserUuid(user.getUuid())).thenReturn(Optional.empty());

        Map<String, String> values = resolver.forMember(user);

        assertThat(render("{{plan.monthlyFee}} · {{plan.maxBeneficiaries}}", values))
                .isEqualTo("[Cuota mensual del plan pendiente] · [Máximo de beneficiarios del plan pendiente]");
    }

    @Test
    void member_beneficiaryCountUsesLocaleSeparators() {
        User user = user(person("María Pérez", "V", "12345678", null));
        Member member = new Member();
        member.setId(5L);
        member.setPerson(user.getPerson());
        when(memberRepository.findByUserUuid(user.getUuid())).thenReturn(Optional.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(5L)).thenReturn(Optional.empty());
        when(beneficiaryRepository.countByMemberIdAndActiveTrue(5L)).thenReturn(15000L);

        assertThat(resolver.forMember(user)).containsEntry("beneficiaries.count", "15.000");
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
        // B2P file number carries the advisor code (hub ADR 0031 §10).
        ally.setSegment(AllyType.Segment.B2P);
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);
        agreement.setStartDate(LocalDate.of(2026, 10, 1));
        agreement.setNoticeDays(45);
        agreement.setAutoRenew(false);
        agreement.setTerms("Descuento adicional los sábados.");

        Map<String, String> values = resolver.forAlly(agreement).values();

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

        Map<String, String> values = resolver.forAlly(agreement).values();

        assertThat(render("{{agreement.autoRenew}} · {{ally.taxId}} · {{ally.representativeName}} · {{agreement.addendum}}",
                values)).isEqualTo("Sí · [RIF del aliado pendiente] · [Representante pendiente] · "
                + "[Condiciones particulares pendiente]");
    }

    @Test
    void ally_ratesAreTheApprovedActiveServicesByNameWithSegmentCategoryDiscountAndExclusions() {
        Ally ally = new Ally();
        ally.setId(9L);
        ally.setName("Clínica Ejemplo, C.A.");
        ally.setSegment(AllyType.Segment.B2P);
        ally.setAllyTypes(Set.of(allyType(AllyType.Segment.B2P)));
        AllyProduct lab = service(2L, "laboratorio clínico", "Exámenes", new BigDecimal("10.50"), "Insumos de alto costo");
        AllyProduct consult = service(1L, "Consulta oftalmológica", "Consultas", new BigDecimal("15.00"), null);
        AllyProduct retired = service(3L, "Cirugía", "Cirugías", new BigDecimal("5"), null);
        retired.setActive(false);
        when(allyProductRepository.findByAllyIdAndReviewStatus(9L, ReviewStatus.APPROVED))
                .thenReturn(List.of(lab, retired, consult));
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);

        List<Map<String, String>> rows = resolver.forAlly(agreement).rows("agreement.rates");

        assertThat(rows).containsExactly(
                Map.of("segment", "B2P", "category", "Consultas", "serviceName", "Consulta oftalmológica",
                        "discountPct", "15"),
                Map.of("segment", "B2P", "category", "Exámenes", "serviceName", "laboratorio clínico",
                        "discountPct", "10,50", "exclusions", "Insumos de alto costo"));
        assertThat(render("{{#agreement.rates}}{{serviceName}}: {{exclusions}}. {{/agreement.rates}}",
                resolver.forAlly(agreement)))
                .isEqualTo("Consulta oftalmológica: Sin exclusiones. laboratorio clínico: Insumos de alto costo. ");
    }

    @Test
    void ally_withoutApprovedServicesRendersTheRatesPendingNote() {
        Ally ally = new Ally();
        ally.setId(9L);
        when(allyProductRepository.findByAllyIdAndReviewStatus(9L, ReviewStatus.APPROVED)).thenReturn(List.of());
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);

        TermsRenderContext context = resolver.forAlly(agreement);

        assertThat(context.rows("agreement.rates")).isEmpty();
        assertThat(render("{{#agreement.rates}}{{serviceName}}{{/agreement.rates}}", context))
                .isEqualTo("[Tarifas del convenio pendientes]");
    }

    @Test
    void ally_notPersistedYetSkipsTheServicesLookup() {
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(new Ally());

        assertThat(resolver.forAlly(agreement).rows("agreement.rates")).isEmpty();
        verifyNoInteractions(allyProductRepository);
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private void officialCurrencyIs(Currency official) {
        Organization organization = new Organization();
        organization.setOfficialCurrency(official);
        when(organizationRepository.findSingleton()).thenReturn(organization);
    }

    private static Currency currency(String code, String name) {
        Currency currency = new Currency();
        currency.setCode(code);
        currency.setName(name);
        return currency;
    }

    private static Plan plan(Currency currency) {
        Plan plan = new Plan();
        plan.setName("Plan Familiar");
        plan.setCurrency(currency);
        return plan;
    }

    /** A user whose member record has an active membership on {@code plan}. */
    private User memberWithPlan(Plan plan) {
        User user = user(person("María Pérez", "V", "12345678", null));
        Member member = new Member();
        member.setId(5L);
        member.setPerson(user.getPerson());
        Membership membership = new Membership();
        membership.setPlan(plan);
        when(memberRepository.findByUserUuid(user.getUuid())).thenReturn(Optional.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(5L)).thenReturn(Optional.of(membership));
        when(beneficiaryRepository.countByMemberIdAndActiveTrue(5L)).thenReturn(0L);
        return user;
    }

    private static String render(String template, Map<String, String> values) {
        return TermsTemplateRenderer.render(template, new Organization(), values);
    }

    private static String render(String template, TermsRenderContext context) {
        return TermsTemplateRenderer.render(template, new Organization(), context);
    }

    private static AllyType allyType(AllyType.Segment segment) {
        AllyType type = new AllyType();
        type.setCode("T-" + segment);
        type.setSegment(segment);
        return type;
    }

    private static AllyProduct service(Long id, String name, String category, BigDecimal discount, String exclusions) {
        ProductCategory productCategory = new ProductCategory();
        productCategory.setName(category);
        Product product = new Product();
        product.setName(name);
        product.setProductCategory(productCategory);
        AllyProduct service = new AllyProduct();
        service.setId(id);
        service.setProduct(product);
        service.setDiscountPct(discount);
        service.setExclusions(exclusions);
        service.setReviewStatus(ReviewStatus.APPROVED);
        return service;
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
