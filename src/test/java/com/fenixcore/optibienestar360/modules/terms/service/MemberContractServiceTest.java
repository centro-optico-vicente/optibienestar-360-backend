package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.service.InscriptionPaymentMethodResolver;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.person.repository.PersonProfessionalLicenseRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.MemberTermsPreviewRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The MEMBER contract rendered for an applicant (public form preview and
 * affiliation requests): the typed data fills the holder and plan values,
 * what is missing renders as its pending note, and the sha256 fingerprint is
 * stable for the same text.
 */
@ExtendWith(MockitoExtension.class)
class MemberContractServiceTest {

    private static final String TEMPLATE =
            "{{member.fullName}} | {{member.document}} | {{member.birthDate}} | {{membership.planName}} | {{membership.activatedAt}}";

    @Mock private TermsVersionService termsVersionService;
    @Mock private PlanRepository planRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private AllyProductRepository allyProductRepository;
    @Mock private PersonProfessionalLicenseRepository licenseRepository;
    @Mock private InscriptionPaymentMethodResolver inscriptionPaymentMethodResolver;

    private MemberContractService service;
    private TermsVersion version;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        TermsVariableResolver resolver = new TermsVariableResolver(memberRepository, membershipRepository,
                beneficiaryRepository, new DisplayFormatter(messageSource), organizationRepository,
                allyProductRepository, licenseRepository, inscriptionPaymentMethodResolver);
        service = new MemberContractService(termsVersionService, new TermsTemplateRenderer(organizationRepository),
                resolver, planRepository);

        version = new TermsVersion();
        version.setUuid(UUID.randomUUID());
        version.setTermType(TermType.MEMBER);
        version.setTitle("Acuerdo de Membresía");
        version.setContentMarkdown(TEMPLATE);
    }

    @Test
    void preview_fillsTheTypedDataAndThePublishedPlan() {
        Plan plan = new Plan();
        plan.setUuid(UUID.randomUUID());
        plan.setName("Plan Individual");
        plan.setActive(true);
        plan.setPublished(true);
        when(termsVersionService.getCurrentPublic(TermType.MEMBER)).thenReturn(version);
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        PublicTermsDto dto = service.preview(TermType.MEMBER, new MemberTermsPreviewRequest(
                " María ", "Pérez", "V", "12345678", LocalDate.of(1985, 3, 15), plan.getUuid()));

        assertThat(dto.uuid()).isEqualTo(version.getUuid());
        assertThat(dto.contentMarkdown())
                .isEqualTo("María Pérez | V-12345678 | 15-03-1985 | Plan Individual | [Fecha de activación pendiente]");
    }

    @Test
    void preview_fillsThePlanFeesInTheirOwnCurrency() {
        Currency usd = new Currency();
        usd.setCode("USD");
        Plan plan = new Plan();
        plan.setUuid(UUID.randomUUID());
        plan.setName("Plan Familiar");
        plan.setActive(true);
        plan.setPublished(true);
        plan.setCurrency(usd);
        plan.setMonthlyFee(new BigDecimal("5.00"));
        version.setContentMarkdown("{{plan.monthlyFee}}");
        when(termsVersionService.getCurrentPublic(TermType.MEMBER)).thenReturn(version);
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        PublicTermsDto dto = service.preview(TermType.MEMBER, new MemberTermsPreviewRequest(
                "Ana", "Ruiz", "V", "1234567", null, plan.getUuid()));

        assertThat(dto.contentMarkdown())
                .contains("5,00")
                .endsWith("o su equivalente en bolívares a la tasa oficial del BCV vigente el día del pago");
    }

    @Test
    void preview_withNothingTypedYet_showsThePendingNotes() {
        when(termsVersionService.getCurrentPublic(TermType.MEMBER)).thenReturn(version);

        PublicTermsDto dto = service.preview(TermType.MEMBER,
                new MemberTermsPreviewRequest(null, null, "V", null, null, null));

        assertThat(dto.contentMarkdown()).startsWith("[Nombre del afiliado pendiente] | [Cédula del afiliado pendiente]");
        verifyNoInteractions(planRepository);
    }

    @Test
    void preview_ignoresAnUnpublishedPlan() {
        Plan hidden = new Plan();
        hidden.setUuid(UUID.randomUUID());
        hidden.setName("Plan Oculto");
        hidden.setActive(true);
        hidden.setPublished(false);
        when(termsVersionService.getCurrentPublic(TermType.MEMBER)).thenReturn(version);
        when(planRepository.findByUuid(hidden.getUuid())).thenReturn(Optional.of(hidden));

        PublicTermsDto dto = service.preview(TermType.MEMBER,
                new MemberTermsPreviewRequest("Ana", "Ruiz", "V", "1234567", null, hidden.getUuid()));

        assertThat(dto.contentMarkdown()).contains("[Plan pendiente]").doesNotContain("Plan Oculto");
    }

    @Test
    void preview_onlyForTheMemberContract() {
        assertThatThrownBy(() -> service.preview(TermType.ADVISOR,
                new MemberTermsPreviewRequest(null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms.preview.member_only");
        verifyNoInteractions(termsVersionService);
    }

    @Test
    void sha256_isAStableHexFingerprint() {
        assertThat(MemberContractService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(MemberContractService.sha256("abc")).isNotEqualTo(MemberContractService.sha256("abd"));
    }
}
