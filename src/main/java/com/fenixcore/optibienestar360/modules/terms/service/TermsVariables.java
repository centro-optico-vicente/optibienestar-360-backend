package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Catalog of the {@code {{...}}} variables a T&C template may use, per
 * contract kind. Stateless, so the renderer, the save-time validation and the
 * admin {@code GET /v1/admin/terms/variables} endpoint share one source.
 *
 * <ul>
 *   <li><b>Common</b> (every kind): {@code brand.name} and the {@code org.*}
 *   organization values.</li>
 *   <li><b>MEMBER</b>: the affiliate holder, their active membership, its
 *   beneficiaries and its plan's fees and beneficiary limits ({@code plan.*}).</li>
 *   <li><b>ADVISOR</b>: the advisor's own record ({@code promoters}).</li>
 *   <li><b>ALLY</b>: the ally and the agreement that links the template,
 *   plus the repeated section {@code agreement.rates} (one row per approved
 *   service of the ally — the agreement's rates table).</li>
 * </ul>
 *
 * <p>A <b>section</b> ({@link Section}) is a block repeated once per row:
 * {@code {{#agreement.rates}} | {{serviceName}} | {{discountPct}} % | {{/agreement.rates}}}.
 * Inside it, the single-word placeholders are the row's fields
 * ({@code {{serviceName}}}); they are only valid there. A section with no rows
 * renders its pending note once.</p>
 *
 * <p>Each variable carries the Spanish note rendered when its value is
 * missing (e.g. {@code [RIF pendiente]}) and a sample value for the admin
 * preview. The description shown in the admin editor lives in the message
 * bundles under {@link #descriptionCode(String)}.</p>
 */
public final class TermsVariables {

    /**
     * How a variable's value is formatted in the contract text (es-VE): plain
     * text, a {@code dd-MM-yyyy} date, a locale-formatted number, an amount in
     * its own currency (see {@link TermsVariableResolver#MONEY_PAYMENT_CLAUSE_VES})
     * or {@code Sí}/{@code No}. {@code LIST} is a repeated {@link Section}
     * (its rows' fields carry their own kind). Exposed to the admin editor as
     * a hint.
     */
    public enum Kind { TEXT, DATE, NUMBER, MONEY, BOOLEAN, LIST }

    /** One template variable: its key, value kind, the note shown when the value is missing, a preview sample. */
    public record Variable(String key, Kind kind, String pendingNote, String sample) {

        /** A {@link Kind#TEXT} variable. */
        public Variable(String key, String pendingNote, String sample) {
            this(key, Kind.TEXT, pendingNote, sample);
        }
    }

    /**
     * A repeated block {@code {{#key}}…{{/key}}}: rendered once per row, its
     * single-word placeholders ({@code {{serviceName}}}) resolved against the
     * row's {@code fields} (a blank field shows that field's pending note).
     * No rows = {@code pendingNote} once, in place of the whole block.
     */
    public record Section(String key, String pendingNote, List<Variable> fields,
                          List<Map<String, String>> sampleRows) {

        /** The row field {@code name}, if this section has it. */
        public Optional<Variable> field(String name) {
            return fields.stream().filter(f -> f.key().equals(name)).findFirst();
        }
    }

    /** Preview sample of a MONEY variable — the shape the resolver produces for a USD plan with VES as official currency. */
    private static String moneySample(String amount) {
        return "US$ " + amount + " " + TermsVariableResolver.MONEY_PAYMENT_CLAUSE_VES;
    }

    public static final String BRAND_NAME = "brand.name";

    /** Placeholder syntax accepted by the renderer: {@code {{group.name}}}, whitespace tolerated. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z]+\\.[A-Za-z]+)\\s*}}");

    /** Anything between double braces — used to validate a template on save. */
    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");

    /**
     * A section block {@code {{#group.name}}…{{/group.name}}} (lazy, so
     * consecutive blocks don't merge; no nesting). Group 1 = key, group 2 =
     * the inner text, group 3 = the line break right after the closing tag.
     */
    static final Pattern SECTION = Pattern.compile(
            "\\{\\{\\s*#\\s*([A-Za-z]+\\.[A-Za-z]+)\\s*}}(.*?)\\{\\{\\s*/\\s*\\1\\s*}}(\\r?\\n)?", Pattern.DOTALL);

    /** Any block {@code {{#x}}…{{/x}}} whatever its key — used to validate a template on save; group 3 = closing tag. */
    private static final Pattern ANY_SECTION = Pattern.compile(
            "\\{\\{\\s*#\\s*([^{}]*?)\\s*}}(.*?)(\\{\\{\\s*/\\s*\\1\\s*}})", Pattern.DOTALL);

    /** A row-field placeholder inside a section: a single word, {@code {{serviceName}}}. */
    static final Pattern ROW_FIELD = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*}}");

    /** Organization values (V181) — resolved from the singleton organization, preview included. */
    static final List<Variable> COMMON = List.of(
            new Variable(BRAND_NAME, null, null),
            new Variable("org.legalName", "[Razón social pendiente]", null),
            new Variable("org.taxId", "[RIF pendiente]", null),
            new Variable("org.registryState", "[Estado del registro pendiente]", null),
            new Variable("org.city", "[Ciudad de firma pendiente]", null),
            new Variable("org.serviceArea", "[Zona de cobertura pendiente]", null)
    );

    static final List<Variable> MEMBER = List.of(
            new Variable("member.fullName", "[Nombre del afiliado pendiente]", "María Fernanda Pérez Gómez"),
            new Variable("member.document", "[Cédula del afiliado pendiente]", "V-12345678"),
            new Variable("member.birthDate", Kind.DATE, "[Fecha de nacimiento pendiente]", "15-03-1985"),
            new Variable("membership.planName", "[Plan pendiente]", "Plan Familiar"),
            new Variable("membership.activatedAt", Kind.DATE, "[Fecha de activación pendiente]", "01-10-2026"),
            new Variable("beneficiaries.count", Kind.NUMBER, "[Beneficiarios pendiente]", "3"),
            new Variable("plan.monthlyFee", Kind.MONEY, "[Cuota mensual del plan pendiente]", moneySample("25,00")),
            new Variable("plan.inscriptionFee", Kind.MONEY, "[Cuota de inscripción del plan pendiente]",
                    moneySample("10,00")),
            new Variable("plan.extraBeneficiaryInscriptionFee", Kind.MONEY,
                    "[Inscripción por beneficiario adicional pendiente]", moneySample("5,00")),
            new Variable("plan.includedBeneficiaries", Kind.NUMBER, "[Beneficiarios incluidos en el plan pendiente]",
                    "3"),
            new Variable("plan.maxBeneficiaries", Kind.NUMBER, "[Máximo de beneficiarios del plan pendiente]", "5")
    );

    static final List<Variable> ADVISOR = List.of(
            new Variable("advisor.code", "[Código de asesor pendiente]", "ASE001"),
            new Variable("advisor.fullName", "[Nombre del asesor pendiente]", "José Antonio Rodríguez"),
            new Variable("advisor.document", "[Cédula del asesor pendiente]", "V-15678901"),
            new Variable("advisor.zone", "[Zona asignada pendiente]", "Acarigua centro"),
            new Variable("advisor.rank", "[Rango pendiente]", "Asesor"),
            new Variable("advisor.joinedAt", Kind.DATE, "[Fecha de ingreso pendiente]", "01-09-2026")
    );

    static final List<Variable> ALLY = List.of(
            new Variable("ally.name", "[Razón social del aliado pendiente]", "Clínica Ejemplo, C.A."),
            new Variable("ally.taxId", "[RIF del aliado pendiente]", "J-12345678-9"),
            new Variable("ally.representativeName", "[Representante pendiente]", "Ana López"),
            new Variable("ally.representativeDocument", "[Documento del representante pendiente]", "V-9876543"),
            new Variable("ally.fileNumber", "[Nº de expediente pendiente]", "ASE001-0007"),
            new Variable("ally.openingHours", "[Horario de atención pendiente]",
                    "Lun–Vie 08:00–12:00, 14:00–18:00; Sáb 08:00–12:00"),
            new Variable("agreement.startDate", Kind.DATE, "[Fecha de inicio del convenio pendiente]", "01-10-2026"),
            new Variable("agreement.noticeDays", Kind.NUMBER, "[Días de preaviso pendiente]", "30"),
            new Variable("agreement.autoRenew", Kind.BOOLEAN, "[Renovación automática pendiente]", "Sí"),
            new Variable("agreement.addendum", "[Condiciones particulares pendiente]",
                    "Descuento adicional del 5 % en consultas los sábados.")
    );

    /** Rates table of the ally agreement: one row per approved, active service of the ally, by name. */
    static final Section AGREEMENT_RATES = new Section("agreement.rates", "[Tarifas del convenio pendientes]",
            List.of(
                    new Variable("segment", "[Segmento pendiente]", "B2P"),
                    new Variable("category", "[Categoría pendiente]", "Consultas"),
                    new Variable("serviceName", "[Servicio pendiente]", "Consulta oftalmológica"),
                    new Variable("discountPct", Kind.NUMBER, "[Descuento pendiente]", "15"),
                    new Variable("discountLabel", "[Tarifa preferencial pendiente]", "15 %"),
                    new Variable("priceLabel", "[Precio pendiente]", "US$ 40,00"),
                    new Variable("finalPriceLabel", "[Precio para el afiliado pendiente]", "US$ 34,00"),
                    new Variable("exclusions", "Sin exclusiones", "Intervenciones quirúrgicas mayores")
            ),
            List.of(
                    Map.of("segment", "B2P", "category", "Consultas", "serviceName", "Consulta oftalmológica",
                            "discountPct", "15", "discountLabel", "15 %", "priceLabel", "US$ 40,00",
                            "finalPriceLabel", "US$ 34,00", "exclusions", "Intervenciones quirúrgicas mayores"),
                    Map.of("segment", "B2P", "category", "Exámenes", "serviceName", "Exámenes de laboratorio",
                            "discountPct", "10,5", "discountLabel", "US$ 4,20", "priceLabel", "US$ 40,00",
                            "finalPriceLabel", "US$ 35,80", "exclusions", "Insumos de alto costo")
            ));

    static final List<Section> ALLY_SECTIONS = List.of(AGREEMENT_RATES);

    private static final Map<String, Section> SECTION_BY_KEY = Stream.of(ALLY_SECTIONS)
            .flatMap(List::stream)
            .collect(Collectors.toMap(Section::key, s -> s, (a, b) -> a, LinkedHashMap::new));

    /** Every per-entity variable (all kinds), by key. */
    private static final Map<String, Variable> ENTITY_BY_KEY = Stream.of(MEMBER, ADVISOR, ALLY)
            .flatMap(List::stream)
            .collect(Collectors.toMap(Variable::key, v -> v, (a, b) -> a, LinkedHashMap::new));

    private static final Map<String, Variable> COMMON_BY_KEY = COMMON.stream()
            .collect(Collectors.toMap(Variable::key, v -> v, (a, b) -> a, LinkedHashMap::new));

    private TermsVariables() {
    }

    /** Variables valid for {@code type}: the common ones first, then the kind's own. */
    public static List<Variable> forType(TermType type) {
        List<Variable> all = new ArrayList<>(COMMON);
        all.addAll(ownOf(type));
        return List.copyOf(all);
    }

    /** Repeated sections valid for {@code type} (only ALLY has one today). */
    public static List<Section> sectionsFor(TermType type) {
        return type == TermType.ALLY ? ALLY_SECTIONS : List.of();
    }

    /**
     * Message-bundle code of a variable's description ({@code terms.variable.<key>});
     * a section's row field is {@code <section key>.<field>}
     * ({@code terms.variable.agreement.rates.serviceName}).
     */
    public static String descriptionCode(String key) {
        return "terms.variable." + key;
    }

    /** Sample values of {@code type}'s own variables, for the admin preview. */
    public static Map<String, String> samplesFor(TermType type) {
        Map<String, String> samples = new HashMap<>();
        for (Variable v : ownOf(type)) {
            samples.put(v.key(), v.sample());
        }
        return samples;
    }

    /** Sample values and section rows of {@code type}, for the admin preview. */
    public static TermsRenderContext sampleContextFor(TermType type) {
        Map<String, List<Map<String, String>>> rows = new HashMap<>();
        for (Section section : sectionsFor(type)) {
            rows.put(section.key(), section.sampleRows());
        }
        return new TermsRenderContext(samplesFor(type), rows);
    }

    /**
     * Placeholders of {@code template} that are not valid for {@code type},
     * in order of first appearance, without duplicates. Empty = valid.
     * A section's row fields ({@code serviceName}) are only valid inside a
     * {@code {{#section}}…{{/section}}} block of a section of {@code type};
     * the section key on its own ({@code {{agreement.rates}}}), a block of an
     * unknown section ({@code #foo.bar}) or an unpaired tag are reported as
     * written (without braces).
     */
    public static List<String> unknownIn(String template, TermType type) {
        if (template == null || !template.contains("{{")) return List.of();
        Set<String> allowed = forType(type).stream().map(Variable::key).collect(Collectors.toSet());
        Map<String, Section> sections = sectionsFor(type).stream()
                .collect(Collectors.toMap(Section::key, s -> s));
        // Spans of the well-formed blocks: [open tag start, close tag end) + their section (null = unknown key).
        List<int[]> spans = new ArrayList<>();
        List<Section> spanSections = new ArrayList<>();
        List<int[]> closeTags = new ArrayList<>();
        Matcher block = ANY_SECTION.matcher(template);
        while (block.find()) {
            spans.add(new int[]{block.start(), block.end()});
            spanSections.add(sections.get(block.group(1)));
            closeTags.add(new int[]{block.start(3), block.end(3)});
        }
        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = ANY_PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            int span = spanAt(spans, matcher.start());
            if (span >= 0 && closeTags.get(span)[0] == matcher.start()) {
                continue; // closing tag of a block: its opening tag already decided
            }
            Section section = span >= 0 ? spanSections.get(span) : null;
            if (key.startsWith("#")) {
                boolean opensKnownBlock = span >= 0 && spans.get(span)[0] == matcher.start() && section != null;
                if (!opensKnownBlock) unknown.add(key.replaceAll("\\s+", ""));
                continue;
            }
            if (key.startsWith("/")) {
                unknown.add(key.replaceAll("\\s+", "")); // a closing tag without its opening one
                continue;
            }
            boolean rowField = section != null && section.field(key).isPresent();
            if (!rowField && !allowed.contains(key)) unknown.add(key);
        }
        return List.copyOf(unknown);
    }

    /** Index of the span containing {@code position}, or -1. */
    private static int spanAt(List<int[]> spans, int position) {
        for (int i = 0; i < spans.size(); i++) {
            if (position >= spans.get(i)[0] && position < spans.get(i)[1]) return i;
        }
        return -1;
    }

    /** The organization variable {@code key}, if it is one. */
    static Optional<Variable> common(String key) {
        return Optional.ofNullable(COMMON_BY_KEY.get(key));
    }

    /** The repeated section {@code key} (any kind), if it is one. */
    static Optional<Section> section(String key) {
        return Optional.ofNullable(SECTION_BY_KEY.get(key));
    }

    /** The per-entity variable {@code key} (any kind), if it is one. */
    static Optional<Variable> entity(String key) {
        return Optional.ofNullable(ENTITY_BY_KEY.get(key));
    }

    private static List<Variable> ownOf(TermType type) {
        return switch (type) {
            case MEMBER -> MEMBER;
            case ADVISOR -> ADVISOR;
            case ALLY -> ALLY;
        };
    }
}
