package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

/**
 * Resolves the {@code {{...}}} placeholders of a stored T&C template
 * ({@code terms_versions.content_markdown}) at serve time. The stored
 * template stays generic: editing the organization, a member, an advisor or
 * an ally updates every served text without publishing a new version.
 *
 * <p>Two sources (catalog in {@link TermsVariables}):</p>
 * <ul>
 *   <li>{@code {{brand.name}}} and {@code {{org.*}}} — the single active
 *   organization, for every kind;</li>
 *   <li>per-entity variables ({@code member.*}, {@code membership.*},
 *   {@code beneficiaries.*}, {@code advisor.*}, {@code ally.*},
 *   {@code agreement.*}, {@code professional.*}) — a value map built for the concrete user by
 *   {@link TermsVariableResolver}, or the sample values for the admin
 *   preview.</li>
 * </ul>
 * A known placeholder whose value is blank renders as a visible bracketed
 * note (e.g. {@code [RIF pendiente]}) instead of an empty string, so a missing
 * value is obvious to the reader. Unknown placeholders are left untouched
 * (save-time validation keeps them out of new templates).
 *
 * <p>Repeated sections ({@link TermsVariables.Section}, e.g. the agreement
 * rates table) are expanded after the scalar placeholders: the block
 * {@code {{#agreement.rates}}…{{/agreement.rates}}} is written once per row of
 * {@link TermsRenderContext#rows(String)}, its single-word placeholders
 * ({@code {{serviceName}}}) resolved against the row. When the opening tag
 * ends its line the block is line-based (one line per row, the tag lines
 * dropped — a markdown table keeps its shape); otherwise rows are written
 * back to back. No rows = the section's pending note once.</p>
 */
@Component
@RequiredArgsConstructor
public class TermsTemplateRenderer {

    /** Commercial brand shown in the contracts (the client documents alternate "360" / "360°"). */
    public static final String BRAND_NAME = "OPTIBIENESTAR 360";

    /** Organization placeholder → value extractor (the fallback note lives in {@link TermsVariables}). */
    private static final Map<String, Function<Organization, String>> ORG_EXTRACTORS = Map.ofEntries(
            Map.entry("org.legalName", (Function<Organization, String>) Organization::getLegalName),
            Map.entry("org.taxId", Organization::getTaxIdentifier),
            Map.entry("org.registryState", Organization::getRegistryState),
            Map.entry("org.city", Organization::getSigningCity),
            Map.entry("org.serviceArea", Organization::getServiceArea),
            Map.entry("org.address", Organization::getAddress),
            Map.entry("org.email", Organization::getEmail),
            Map.entry("org.phones", Organization::getPhones),
            Map.entry("org.signatoryName", Organization::getSignatoryName),
            Map.entry("org.signatoryDocument", TermsTemplateRenderer::signatoryDocument),
            Map.entry("org.signatoryPosition", Organization::getSignatoryPosition)
    );

    private final OrganizationRepository organizationRepository;

    /** {@code "V-12345678"} of the authorized signatory; {@code null} without a number. */
    private static String signatoryDocument(Organization organization) {
        return TermsVariableResolver.document(organization.getSignatoryDocumentType(),
                organization.getSignatoryDocumentNumber());
    }

    /** Renders {@code template} against the organization only; every per-entity value shows its pending note. */
    public String render(String template) {
        return render(template, Map.of());
    }

    /** Renders {@code template} against the organization and the per-entity {@code values}. */
    public String render(String template, Map<String, String> values) {
        return render(template, TermsRenderContext.of(values));
    }

    /** Renders {@code template} against the organization and the per-entity values and section rows. */
    public String render(String template, TermsRenderContext context) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        return render(template, organizationRepository.findSingleton(), context);
    }

    /** Admin preview: the real organization plus {@code type}'s sample values and section rows. */
    public String renderSample(String template, TermType type) {
        return render(template, TermsVariables.sampleContextFor(type));
    }

    /** Pure rendering against a given organization ({@code null} = every org value pending), no entity values. */
    public static String render(String template, Organization organization) {
        return render(template, organization, Map.of());
    }

    /**
     * Pure rendering against a given organization ({@code null} = every org
     * value pending) and per-entity {@code values} (missing or blank key =
     * pending note).
     */
    public static String render(String template, Organization organization, Map<String, String> values) {
        return render(template, organization, TermsRenderContext.of(values));
    }

    /**
     * Pure rendering against a given organization ({@code null} = every org
     * value pending) and a per-entity {@code context} ({@code null} = every
     * per-entity value and section pending).
     */
    public static String render(String template, Organization organization, TermsRenderContext context) {
        if (template == null) {
            return null;
        }
        TermsRenderContext entity = context == null ? TermsRenderContext.EMPTY : context;
        Matcher matcher = TermsVariables.PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = resolve(key, organization, entity.values());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(out);
        return expandSections(out.toString(), entity);
    }

    /** Expands every known section block of {@code text}; unknown ones are left as written. */
    private static String expandSections(String text, TermsRenderContext context) {
        if (!text.contains("{{")) {
            return text;
        }
        Matcher matcher = TermsVariables.SECTION.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String replacement = TermsVariables.section(matcher.group(1))
                    .map(section -> expand(section, matcher.group(2), matcher.group(3), context.rows(section.key())))
                    .orElse(matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * One block: {@code inner} written per row. Line-based when the opening
     * tag ends its line ({@code inner} starts with a line break): that break
     * and the one after the closing tag ({@code trailingBreak}) are dropped and
     * each row ends its own line. Inline otherwise: rows back to back and the
     * trailing break kept.
     */
    private static String expand(TermsVariables.Section section, String inner, String trailingBreak,
                                 List<Map<String, String>> rows) {
        String body = inner;
        boolean lineBased = body.startsWith("\n") || body.startsWith("\r\n");
        if (lineBased) {
            body = body.substring(body.startsWith("\r\n") ? 2 : 1).replaceAll("[ \\t]+$", "");
            if (!body.endsWith("\n")) body = body + "\n";
        }
        String after = lineBased || trailingBreak == null ? "" : trailingBreak;
        if (rows.isEmpty()) {
            return section.pendingNote() + (lineBased ? "\n" : "") + after;
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, String> row : rows) {
            out.append(renderRow(section, body, row == null ? Map.of() : row));
        }
        return out.append(after).toString();
    }

    /** {@code body} with the row fields of {@code section} resolved against {@code row}; other words untouched. */
    private static String renderRow(TermsVariables.Section section, String body, Map<String, String> row) {
        Matcher matcher = TermsVariables.ROW_FIELD.matcher(body);
        StringBuilder out = new StringBuilder(body.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = section.field(name)
                    .map(field -> orPending(row.get(name), field.pendingNote()))
                    .orElse(matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** {@code null} = not a known variable, leave the placeholder as written. */
    private static String resolve(String key, Organization organization, Map<String, String> values) {
        if (TermsVariables.BRAND_NAME.equals(key)) {
            return BRAND_NAME;
        }
        Function<Organization, String> extractor = ORG_EXTRACTORS.get(key);
        if (extractor != null) {
            String value = organization == null ? null : extractor.apply(organization);
            return orPending(value, TermsVariables.common(key).map(TermsVariables.Variable::pendingNote).orElse(""));
        }
        return TermsVariables.entity(key)
                .map(variable -> orPending(values.get(key), variable.pendingNote()))
                .orElse(null);
    }

    private static String orPending(String value, String pendingNote) {
        return value == null || value.isBlank() ? pendingNote : value.trim();
    }
}
