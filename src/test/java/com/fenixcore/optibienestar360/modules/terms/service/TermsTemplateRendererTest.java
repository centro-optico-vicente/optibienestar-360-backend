package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TermsTemplateRenderer} — placeholder resolution of the
 * stored T&C templates against the organization data (V181) and the
 * per-entity variable values ({@link TermsVariables}).
 */
@ExtendWith(MockitoExtension.class)
class TermsTemplateRendererTest {

    @Mock
    private OrganizationRepository organizationRepository;

    private static Organization organization() {
        Organization org = new Organization();
        org.setName("OptiBienestar 360");
        org.setLegalName("CENTRO ÓPTICO VICENTE CONSULTING, C.A.");
        org.setTaxIdentifier("J-12345678-9");
        org.setRegistryState("Portuguesa");
        org.setSigningCity("Acarigua");
        org.setServiceArea("eje Acarigua-Araure");
        return org;
    }

    @Test
    void resolvesEveryOrganizationPlaceholderAndTheBrand() {
        String template = "Entre **{{org.legalName}}** (\"**{{brand.name}}**\"), Registro Mercantil del estado "
                + "{{org.registryState}}, RIF **{{org.taxId}}**, en el {{org.serviceArea}}, ciudad de {{org.city}}.";

        String rendered = TermsTemplateRenderer.render(template, organization());

        assertThat(rendered).isEqualTo("Entre **CENTRO ÓPTICO VICENTE CONSULTING, C.A.** (\"**OPTIBIENESTAR 360**\"), "
                + "Registro Mercantil del estado Portuguesa, RIF **J-12345678-9**, en el eje Acarigua-Araure, "
                + "ciudad de Acarigua.");
    }

    @Test
    void blankValuesRenderAsVisiblePendingNotes() {
        Organization org = organization();
        org.setTaxIdentifier(null);
        org.setRegistryState("  ");

        String rendered = TermsTemplateRenderer.render("RIF {{org.taxId}} · estado {{org.registryState}}", org);

        assertThat(rendered).isEqualTo("RIF [RIF pendiente] · estado [Estado del registro pendiente]");
    }

    @Test
    void nullOrganizationStillResolvesTheBrand() {
        String rendered = TermsTemplateRenderer.render("{{brand.name}} / {{org.city}}", null);

        assertThat(rendered).isEqualTo("OPTIBIENESTAR 360 / [Ciudad de firma pendiente]");
    }

    @Test
    void unknownPlaceholdersAreLeftUntouchedAndWhitespaceIsTolerated() {
        String rendered = TermsTemplateRenderer.render("{{ org.taxId }} {{foo.bar}}", organization());

        assertThat(rendered).isEqualTo("J-12345678-9 {{foo.bar}}");
    }

    @Test
    void entityVariablesResolveFromTheValueMap() {
        String template = "{{member.fullName}} · C.I. {{ member.document }} · {{advisor.code}} · {{ally.fileNumber}}";

        String rendered = TermsTemplateRenderer.render(template, organization(), Map.of(
                "member.fullName", "Ana Pérez",
                "member.document", "V-12345678",
                "advisor.code", "ASE001",
                "ally.fileNumber", "ASE001-0007"));

        assertThat(rendered).isEqualTo("Ana Pérez · C.I. V-12345678 · ASE001 · ASE001-0007");
    }

    @Test
    void missingOrBlankEntityValuesRenderAsPendingNotes() {
        Map<String, String> values = new HashMap<>();
        values.put("member.fullName", "  ");
        values.put("member.document", null);

        String rendered = TermsTemplateRenderer.render(
                "{{member.fullName}} / {{member.document}} / {{membership.planName}} / {{agreement.noticeDays}}",
                organization(), values);

        assertThat(rendered).isEqualTo("[Nombre del afiliado pendiente] / [Cédula del afiliado pendiente] / "
                + "[Plan pendiente] / [Días de preaviso pendiente]");
    }

    @Test
    void organizationOnlyRenderShowsEveryEntityValueAsPending() {
        assertThat(TermsTemplateRenderer.render("{{advisor.fullName}} ({{advisor.rank}})", organization()))
                .isEqualTo("[Nombre del asesor pendiente] ([Rango pendiente])");
    }

    @Test
    void renderSampleUsesTheTypesSampleValuesAndTheRealOrganization() {
        when(organizationRepository.findSingleton()).thenReturn(organization());
        TermsTemplateRenderer renderer = new TermsTemplateRenderer(organizationRepository);

        assertThat(renderer.renderSample("{{org.city}}: {{agreement.autoRenew}}, {{agreement.noticeDays}} días", TermType.ALLY))
                .isEqualTo("Acarigua: Sí, 30 días");
    }

    @Test
    void valuesWithRegexSpecialCharactersAreInsertedLiterally() {
        Organization org = organization();
        org.setLegalName("ACME $1 \\ C.A.");

        assertThat(TermsTemplateRenderer.render("{{org.legalName}}", org)).isEqualTo("ACME $1 \\ C.A.");
    }

    @Test
    void instanceRenderLoadsTheSingletonOrganization() {
        when(organizationRepository.findSingleton()).thenReturn(organization());
        TermsTemplateRenderer renderer = new TermsTemplateRenderer(organizationRepository);

        assertThat(renderer.render("{{org.city}}")).isEqualTo("Acarigua");
    }

    @Test
    void textWithoutPlaceholdersSkipsTheOrganizationLookup() {
        TermsTemplateRenderer renderer = new TermsTemplateRenderer(organizationRepository);

        assertThat(renderer.render("Sin variables")).isEqualTo("Sin variables");
        assertThat(renderer.render(null)).isNull();
        verifyNoInteractions(organizationRepository);
    }
}
