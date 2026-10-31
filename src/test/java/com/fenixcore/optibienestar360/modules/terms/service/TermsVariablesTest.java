package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Catalog of {@link TermsVariables}: per-kind lists, samples and save-time validation. */
class TermsVariablesTest {

    @Test
    void everyKindGetsTheCommonVariablesFirstThenItsOwn() {
        List<String> member = keys(TermType.MEMBER);
        List<String> advisor = keys(TermType.ADVISOR);
        List<String> ally = keys(TermType.ALLY);

        assertThat(member).startsWith("brand.name", "org.legalName", "org.taxId", "org.registryState",
                "org.city", "org.serviceArea");
        assertThat(member).contains("member.fullName", "member.document", "member.birthDate",
                "membership.planName", "membership.activatedAt", "beneficiaries.count");
        assertThat(advisor).contains("advisor.code", "advisor.fullName", "advisor.document", "advisor.zone",
                "advisor.rank", "advisor.joinedAt");
        assertThat(ally).contains("ally.name", "ally.taxId", "ally.representativeName",
                "ally.representativeDocument", "ally.fileNumber", "agreement.startDate", "agreement.noticeDays",
                "agreement.autoRenew", "agreement.addendum");
        assertThat(member).doesNotContain("advisor.code", "ally.name");
        assertThat(advisor).doesNotContain("member.fullName", "ally.name");
        assertThat(ally).doesNotContain("member.fullName", "advisor.code");
    }

    @Test
    void everyOwnVariableHasAPendingNoteAndASample() {
        for (TermType type : TermType.values()) {
            assertThat(TermsVariables.samplesFor(type)).allSatisfy((key, sample) -> assertThat(sample).isNotBlank());
            TermsVariables.forType(type).stream()
                    .filter(v -> !v.key().equals(TermsVariables.BRAND_NAME))
                    .forEach(v -> assertThat(v.pendingNote()).as(v.key()).startsWith("[").endsWith("]"));
        }
    }

    @Test
    void member_offersThePlanAmountsAsMoneyAndItsLimitsAsNumbers() {
        assertThat(TermsVariables.forType(TermType.MEMBER))
                .filteredOn(v -> v.key().startsWith("plan."))
                .extracting(TermsVariables.Variable::key, TermsVariables.Variable::kind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("plan.monthlyFee", TermsVariables.Kind.MONEY),
                        org.assertj.core.groups.Tuple.tuple("plan.inscriptionFee", TermsVariables.Kind.MONEY),
                        org.assertj.core.groups.Tuple.tuple("plan.extraBeneficiaryInscriptionFee", TermsVariables.Kind.MONEY),
                        org.assertj.core.groups.Tuple.tuple("plan.includedBeneficiaries", TermsVariables.Kind.NUMBER),
                        org.assertj.core.groups.Tuple.tuple("plan.maxBeneficiaries", TermsVariables.Kind.NUMBER));
        assertThat(TermsVariables.samplesFor(TermType.MEMBER).get("plan.monthlyFee"))
                .isEqualTo("US$ 25,00 o su equivalente en bolívares a la tasa oficial del BCV vigente el día del pago");
    }

    @Test
    void unknownIn_planVariablesAreOnlyValidForMember() {
        assertThat(TermsVariables.unknownIn("{{plan.monthlyFee}} {{plan.maxBeneficiaries}}", TermType.MEMBER)).isEmpty();
        assertThat(TermsVariables.unknownIn("{{plan.monthlyFee}}", TermType.ALLY)).containsExactly("plan.monthlyFee");
        assertThat(TermsVariables.unknownIn("{{plan.monthlyFee}}", TermType.ADVISOR)).containsExactly("plan.monthlyFee");
        assertThat(TermsVariables.unknownIn("{{plan.discount}}", TermType.MEMBER)).containsExactly("plan.discount");
    }

    @Test
    void unknownIn_listsPlaceholdersOfOtherKindsAndMadeUpOnesOnceInOrder() {
        String template = "{{org.city}} {{advisor.code}} {{ member.fullName }} {{foo}} {{advisor.code}} {{ally.name}}";

        assertThat(TermsVariables.unknownIn(template, TermType.MEMBER))
                .containsExactly("advisor.code", "foo", "ally.name");
    }

    @Test
    void unknownIn_isEmptyForAValidTemplateOrPlainText() {
        assertThat(TermsVariables.unknownIn("{{brand.name}} {{ally.fileNumber}}", TermType.ALLY)).isEmpty();
        assertThat(TermsVariables.unknownIn("Sin variables", TermType.ADVISOR)).isEmpty();
        assertThat(TermsVariables.unknownIn(null, TermType.ADVISOR)).isEmpty();
    }

    @Test
    void theV181SeedBodiesOnlyUseCommonVariables() {
        String seedLike = "Entre **{{org.legalName}}** (\"**{{brand.name}}**\"), estado {{org.registryState}}, "
                + "RIF **{{org.taxId}}**, en el {{org.serviceArea}}, ciudad de {{org.city}}.";
        for (TermType type : TermType.values()) {
            assertThat(TermsVariables.unknownIn(seedLike, type)).isEmpty();
        }
    }

    @Test
    void theV186IdentificationBlocksOnlyUseVariablesOfTheirType() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V186__terms_seed_identification_blocks.sql")) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher row = Pattern.compile("\\('(AFILIADO|ASESOR|ALIADO)',\\s*'[0-9a-f]{32}',\\s*\\$h\\$(.*?)\\$h\\$,\\s*\\$f\\$(.*?)\\$f\\$\\)",
                Pattern.DOTALL).matcher(sql);
        int rows = 0;
        while (row.find()) {
            rows++;
            TermType type = TermType.fromCode(row.group(1));
            String blocks = row.group(2) + row.group(3);
            assertThat(blocks).as(type.code()).contains("{{");
            assertThat(TermsVariables.unknownIn(blocks, type)).as(type.code()).isEmpty();
        }
        assertThat(rows).isEqualTo(3);
    }

    // ─── Sections ───────────────────────────────────────────────────────────

    @Test
    void ally_offersTheAgreementRatesSectionWithItsRowFields() {
        assertThat(TermsVariables.sectionsFor(TermType.ALLY)).singleElement().satisfies(section -> {
            assertThat(section.key()).isEqualTo("agreement.rates");
            assertThat(section.pendingNote()).isEqualTo("[Tarifas del convenio pendientes]");
            assertThat(section.fields()).extracting(TermsVariables.Variable::key)
                    .containsExactly("segment", "category", "serviceName", "discountPct", "discountLabel",
                            "priceLabel", "finalPriceLabel", "exclusions");
            assertThat(section.field("discountPct")).get().extracting(TermsVariables.Variable::kind)
                    .isEqualTo(TermsVariables.Kind.NUMBER);
            assertThat(section.sampleRows()).isNotEmpty();
        });
        assertThat(TermsVariables.sectionsFor(TermType.MEMBER)).isEmpty();
        assertThat(TermsVariables.sectionsFor(TermType.ADVISOR)).isEmpty();
        assertThat(keys(TermType.ALLY)).doesNotContain("agreement.rates");
    }

    @Test
    void unknownIn_acceptsTheRatesBlockWithItsRowFieldsAndScalarsInside() {
        String template = """
                | Servicio | Descuento | Exclusiones |
                |---|---|---|
                {{#agreement.rates}}
                | {{ segment }}: {{serviceName}} ({{category}}) | {{discountPct}} % | {{exclusions}} · {{ally.name}} |
                {{/agreement.rates}}
                {{# agreement.rates }}{{serviceName}}{{/ agreement.rates }}""";

        assertThat(TermsVariables.unknownIn(template, TermType.ALLY)).isEmpty();
    }

    @Test
    void unknownIn_rowFieldsAreOnlyValidInsideTheirBlock() {
        assertThat(TermsVariables.unknownIn("{{serviceName}} {{#agreement.rates}}{{price}}{{/agreement.rates}}",
                TermType.ALLY)).containsExactly("serviceName", "price");
    }

    @Test
    void unknownIn_rejectsTheSectionKeyAloneUnknownOrUnpairedBlocksAndOtherKindsSections() {
        assertThat(TermsVariables.unknownIn("{{agreement.rates}}", TermType.ALLY)).containsExactly("agreement.rates");
        assertThat(TermsVariables.unknownIn("{{#foo.bar}}{{serviceName}}{{/foo.bar}}", TermType.ALLY))
                .containsExactly("#foo.bar", "serviceName");
        assertThat(TermsVariables.unknownIn("{{#agreement.rates}} {{serviceName}}", TermType.ALLY))
                .containsExactly("#agreement.rates", "serviceName");
        assertThat(TermsVariables.unknownIn("{{serviceName}} {{/agreement.rates}}", TermType.ALLY))
                .containsExactly("serviceName", "/agreement.rates");
        assertThat(TermsVariables.unknownIn("{{#agreement.rates}}{{serviceName}}{{/agreement.rates}}", TermType.MEMBER))
                .containsExactly("#agreement.rates", "serviceName");
    }

    @Test
    void sampleContextFor_ally_carriesTheScalarSamplesAndTheSampleRows() {
        TermsRenderContext context = TermsVariables.sampleContextFor(TermType.ALLY);

        assertThat(context.values()).containsEntry("agreement.noticeDays", "30");
        assertThat(context.rows("agreement.rates")).isNotEmpty()
                .allSatisfy(row -> assertThat(row).containsKeys("serviceName", "discountPct"));
        assertThat(TermsVariables.sampleContextFor(TermType.MEMBER).sections()).isEmpty();
    }

    private static List<String> keys(TermType type) {
        return TermsVariables.forType(type).stream().map(TermsVariables.Variable::key).toList();
    }
}
