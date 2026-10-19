package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
 *   {@code agreement.*}) — a value map built for the concrete user by
 *   {@link TermsVariableResolver}, or the sample values for the admin
 *   preview.</li>
 * </ul>
 * A known placeholder whose value is blank renders as a visible bracketed
 * note (e.g. {@code [RIF pendiente]}) instead of an empty string, so a missing
 * value is obvious to the reader. Unknown placeholders are left untouched
 * (save-time validation keeps them out of new templates).
 */
@Component
@RequiredArgsConstructor
public class TermsTemplateRenderer {

    /** Commercial brand shown in the contracts (the client documents alternate "360" / "360°"). */
    public static final String BRAND_NAME = "OPTIBIENESTAR 360";

    /** Organization placeholder → value extractor (the fallback note lives in {@link TermsVariables}). */
    private static final Map<String, Function<Organization, String>> ORG_EXTRACTORS = Map.of(
            "org.legalName", Organization::getLegalName,
            "org.taxId", Organization::getTaxIdentifier,
            "org.registryState", Organization::getRegistryState,
            "org.city", Organization::getSigningCity,
            "org.serviceArea", Organization::getServiceArea
    );

    private final OrganizationRepository organizationRepository;

    /** Renders {@code template} against the organization only; every per-entity value shows its pending note. */
    public String render(String template) {
        return render(template, Map.of());
    }

    /** Renders {@code template} against the organization and the per-entity {@code values}. */
    public String render(String template, Map<String, String> values) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        return render(template, organizationRepository.findSingleton(), values);
    }

    /** Admin preview: the real organization plus {@code type}'s sample values. */
    public String renderSample(String template, TermType type) {
        return render(template, TermsVariables.samplesFor(type));
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
        if (template == null) {
            return null;
        }
        Map<String, String> entityValues = values == null ? Map.of() : values;
        Matcher matcher = TermsVariables.PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = resolve(key, organization, entityValues);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
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
