package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TermsTemplateRenderer} — placeholder resolution of the
 * stored T&C templates against the organization data (V181).
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
        String rendered = TermsTemplateRenderer.render("{{ org.taxId }} {{member.fullName}}", organization());

        assertThat(rendered).isEqualTo("J-12345678-9 {{member.fullName}}");
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
