package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
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

        assertThat(rendered).isEqualTo("Entre **CENTRO ÓPTICO VICENTE CONSULTING, C.A.** (\"**OptiBienestar 360**\"), "
                + "Registro Mercantil del estado Portuguesa, RIF **J-12345678-9**, en el eje Acarigua-Araure, "
                + "ciudad de Acarigua.");
    }

    @Test
    void resolvesTheInstitutionalContactOfTheFooter() {
        Organization org = organization();
        org.setAddress("Av. 30 con Calle 21, C.C. Ciudad Cristal, Acarigua");
        org.setEmail("planoptibienestar360@gmail.com");
        org.setPhones("(+58)414-5738530, (+58)424-5984590");

        String rendered = TermsTemplateRenderer.render(
                "{{org.address}} · Email: {{org.email}} · Teléfonos: {{org.phones}}", org);

        assertThat(rendered).isEqualTo("Av. 30 con Calle 21, C.C. Ciudad Cristal, Acarigua · "
                + "Email: planoptibienestar360@gmail.com · Teléfonos: (+58)414-5738530, (+58)424-5984590");
        assertThat(TermsTemplateRenderer.render("{{org.address}} · {{org.email}} · {{org.phones}}", organization()))
                .isEqualTo("[Dirección de la organización pendiente] · [Correo de la organización pendiente] · "
                        + "[Teléfonos de la organización pendientes]");
    }

    @Test
    void licenseVariablesResolvePerLicensingBodyCode() {
        String template = "M.P.P.S. {{professional.licenses.MPPS}} · CM {{professional.licenses.COLEGIO_MEDICOS}}"
                + " · {{professional.licenses.CIV}} · {{professional.licenses.foo}}";

        String rendered = TermsTemplateRenderer.render(template, organization(),
                Map.of("professional.licenses.MPPS", "123456", "professional.licenses.CIV", "98765"));

        assertThat(rendered).isEqualTo("M.P.P.S. 123456 · CM [Nº de Colegio de Médicos pendiente] · 98765"
                + " · {{professional.licenses.foo}}");
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
    void brandIsTheOrganizationsCommercialNameWhenSet() {
        Organization org = organization();
        org.setName("  OptiBienestar 360 ");

        assertThat(TermsTemplateRenderer.render("{{brand.name}} · {{org.legalName}}", org))
                .isEqualTo("OptiBienestar 360 · CENTRO ÓPTICO VICENTE CONSULTING, C.A.");
    }

    @Test
    void blankCommercialNameFallsBackToTheDefaultBrand() {
        Organization org = organization();
        org.setName("   ");

        assertThat(TermsTemplateRenderer.render("{{brand.name}}", org)).isEqualTo("OPTIBIENESTAR 360");
    }

    @Test
    void nullOrganizationStillResolvesTheBrand() {
        String rendered = TermsTemplateRenderer.render("{{brand.name}} / {{org.city}}", (Organization) null);

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

    // ─── Repeated sections ({{#agreement.rates}}…{{/agreement.rates}}) ──────

    private static final String RATES_TABLE = """
            | Servicio | Descuento | Exclusiones |
            |---|---|---|
            {{#agreement.rates}}
            | {{segment}}: {{serviceName}} | {{discountPct}} % | {{exclusions}} |
            {{/agreement.rates}}

            Firmado en {{org.city}}.""";

    private static TermsRenderContext rates(List<Map<String, String>> rows) {
        return new TermsRenderContext(Map.of(), Map.of("agreement.rates", rows));
    }

    @Test
    void section_writesOneTableLinePerRowAndDropsTheTagLines() {
        Map<String, String> consult = Map.of("segment", "B2P", "serviceName", "Consulta", "discountPct", "15",
                "exclusions", "Cirugías mayores");
        Map<String, String> lab = Map.of("segment", "B2P", "serviceName", "Laboratorio", "discountPct", "10,5");

        String rendered = TermsTemplateRenderer.render(RATES_TABLE, organization(), rates(List.of(consult, lab)));

        assertThat(rendered).isEqualTo("""
                | Servicio | Descuento | Exclusiones |
                |---|---|---|
                | B2P: Consulta | 15 % | Cirugías mayores |
                | B2P: Laboratorio | 10,5 % | Sin exclusiones |

                Firmado en Acarigua.""");
    }

    @Test
    void section_withoutRowsRendersItsPendingNoteOnce() {
        String expected = """
                | Servicio | Descuento | Exclusiones |
                |---|---|---|
                [Tarifas del convenio pendientes]

                Firmado en Acarigua.""";

        assertThat(TermsTemplateRenderer.render(RATES_TABLE, organization(), rates(List.of()))).isEqualTo(expected);
        assertThat(TermsTemplateRenderer.render(RATES_TABLE, organization(), TermsRenderContext.EMPTY))
                .isEqualTo(expected);
        assertThat(TermsTemplateRenderer.render(RATES_TABLE, organization())).isEqualTo(expected);
    }

    @Test
    void section_inlineBlockWritesRowsBackToBackAndResolvesScalarsInside() {
        String template = "Tarifas en {{org.city}}:{{#agreement.rates}} {{serviceName}} ({{discountPct}} %, {{org.city}});"
                + "{{/agreement.rates}} fin.";
        Map<String, String> row = new HashMap<>();
        row.put("serviceName", "Consulta");
        row.put("discountPct", "");

        assertThat(TermsTemplateRenderer.render(template, organization(), rates(List.of(row, Map.of("serviceName", "Lentes")))))
                .isEqualTo("Tarifas en Acarigua: Consulta ([Descuento pendiente] %, Acarigua);"
                        + " Lentes ([Descuento pendiente] %, Acarigua); fin.");
        assertThat(TermsTemplateRenderer.render(template, organization(), rates(List.of())))
                .isEqualTo("Tarifas en Acarigua:[Tarifas del convenio pendientes] fin.");
    }

    @Test
    void section_rowFieldsOutsideTheBlockAndUnknownWordsInsideAreLeftUntouched() {
        String template = "{{serviceName}} / {{#agreement.rates}}{{serviceName}} {{price}}{{/agreement.rates}}";

        assertThat(TermsTemplateRenderer.render(template, organization(), rates(List.of(Map.of("serviceName", "Consulta")))))
                .isEqualTo("{{serviceName}} / Consulta {{price}}");
    }

    @Test
    void section_unknownSectionIsLeftAsWritten() {
        String template = "{{#foo.bar}}{{serviceName}}{{/foo.bar}}";

        assertThat(TermsTemplateRenderer.render(template, organization(), rates(List.of(Map.of("serviceName", "X")))))
                .isEqualTo(template);
    }

    @Test
    void section_rowValuesWithRegexSpecialCharactersAreInsertedLiterally() {
        assertThat(TermsTemplateRenderer.render("{{#agreement.rates}}{{exclusions}}{{/agreement.rates}}",
                organization(), rates(List.of(Map.of("exclusions", "$1 \\ licores")))))
                .isEqualTo("$1 \\ licores");
    }

    @Test
    void renderSample_fillsTheRatesSectionWithSampleRows() {
        when(organizationRepository.findSingleton()).thenReturn(organization());
        TermsTemplateRenderer renderer = new TermsTemplateRenderer(organizationRepository);

        assertThat(renderer.renderSample("{{#agreement.rates}}\n| {{serviceName}} | {{discountPct}} % |\n{{/agreement.rates}}",
                TermType.ALLY))
                .isEqualTo("| Consulta oftalmológica | 15 % |\n| Exámenes de laboratorio | 10,5 % |\n");
    }
}
