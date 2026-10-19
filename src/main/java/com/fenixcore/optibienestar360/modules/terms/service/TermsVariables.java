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
 *   <li><b>MEMBER</b>: the affiliate holder, their active membership and its
 *   beneficiaries.</li>
 *   <li><b>ADVISOR</b>: the advisor's own record ({@code promoters}).</li>
 *   <li><b>ALLY</b>: the ally and the agreement that links the template.</li>
 * </ul>
 *
 * <p>Each variable carries the Spanish note rendered when its value is
 * missing (e.g. {@code [RIF pendiente]}) and a sample value for the admin
 * preview. The description shown in the admin editor lives in the message
 * bundles under {@link #descriptionCode(String)}.</p>
 */
public final class TermsVariables {

    /** One template variable: its key, the note shown when the value is missing, a preview sample. */
    public record Variable(String key, String pendingNote, String sample) {
    }

    public static final String BRAND_NAME = "brand.name";

    /** Placeholder syntax accepted by the renderer: {@code {{group.name}}}, whitespace tolerated. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z]+\\.[A-Za-z]+)\\s*}}");

    /** Anything between double braces — used to validate a template on save. */
    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");

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
            new Variable("member.birthDate", "[Fecha de nacimiento pendiente]", "15-03-1985"),
            new Variable("membership.planName", "[Plan pendiente]", "Plan Familiar"),
            new Variable("membership.activatedAt", "[Fecha de activación pendiente]", "01-10-2026"),
            new Variable("beneficiaries.count", "[Beneficiarios pendiente]", "3")
    );

    static final List<Variable> ADVISOR = List.of(
            new Variable("advisor.code", "[Código de asesor pendiente]", "ASE001"),
            new Variable("advisor.fullName", "[Nombre del asesor pendiente]", "José Antonio Rodríguez"),
            new Variable("advisor.document", "[Cédula del asesor pendiente]", "V-15678901"),
            new Variable("advisor.zone", "[Zona asignada pendiente]", "Acarigua centro"),
            new Variable("advisor.rank", "[Rango pendiente]", "Asesor"),
            new Variable("advisor.joinedAt", "[Fecha de ingreso pendiente]", "01-09-2026")
    );

    static final List<Variable> ALLY = List.of(
            new Variable("ally.name", "[Razón social del aliado pendiente]", "Clínica Ejemplo, C.A."),
            new Variable("ally.taxId", "[RIF del aliado pendiente]", "J-12345678-9"),
            new Variable("ally.representativeName", "[Representante pendiente]", "Ana López"),
            new Variable("ally.representativeDocument", "[Documento del representante pendiente]", "V-9876543"),
            new Variable("ally.fileNumber", "[Nº de expediente pendiente]", "ASE001-0007"),
            new Variable("agreement.startDate", "[Fecha de inicio del convenio pendiente]", "01-10-2026"),
            new Variable("agreement.noticeDays", "[Días de preaviso pendiente]", "30"),
            new Variable("agreement.autoRenew", "[Renovación automática pendiente]", "Sí"),
            new Variable("agreement.addendum", "[Condiciones particulares pendiente]",
                    "Descuento adicional del 5 % en consultas los sábados.")
    );

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

    /** Message-bundle code of a variable's description ({@code terms.variable.<key>}). */
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

    /**
     * Placeholders of {@code template} that are not valid for {@code type},
     * in order of first appearance, without duplicates. Empty = valid.
     */
    public static List<String> unknownIn(String template, TermType type) {
        if (template == null || !template.contains("{{")) return List.of();
        Set<String> allowed = forType(type).stream().map(Variable::key).collect(Collectors.toSet());
        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = ANY_PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (!allowed.contains(key)) unknown.add(key);
        }
        return List.copyOf(unknown);
    }

    /** The organization variable {@code key}, if it is one. */
    static Optional<Variable> common(String key) {
        return Optional.ofNullable(COMMON_BY_KEY.get(key));
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
