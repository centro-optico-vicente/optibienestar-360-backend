package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the {@code {{...}}} placeholders of a stored T&C template
 * ({@code terms_versions.content_markdown}) against the organization data at
 * serve time. The stored template stays generic so editing the organization
 * (legal name, RIF, registry state…) updates every served text without
 * publishing a new version.
 *
 * <p>Supported: {@code {{brand.name}}}, {@code {{org.legalName}}},
 * {@code {{org.taxId}}}, {@code {{org.registryState}}}, {@code {{org.city}}},
 * {@code {{org.serviceArea}}}. A known placeholder whose value is blank renders
 * as a visible bracketed note (e.g. {@code [RIF pendiente]}) instead of an
 * empty string, so a missing value is obvious to the reader. Unknown
 * placeholders are left untouched.</p>
 */
@Component
@RequiredArgsConstructor
public class TermsTemplateRenderer {

    /** Commercial brand shown in the contracts (the client documents alternate "360" / "360°"). */
    public static final String BRAND_NAME = "OPTIBIENESTAR 360";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z]+\\.[A-Za-z]+)\\s*}}");

    private static final String BRAND_PLACEHOLDER = "brand.name";

    /** Organization placeholder → (value extractor, Spanish fallback shown when blank). */
    private static final Map<String, Resolver> RESOLVERS = Map.of(
            "org.legalName", new Resolver(Organization::getLegalName, "[Razón social pendiente]"),
            "org.taxId", new Resolver(Organization::getTaxIdentifier, "[RIF pendiente]"),
            "org.registryState", new Resolver(Organization::getRegistryState, "[Estado del registro pendiente]"),
            "org.city", new Resolver(Organization::getSigningCity, "[Ciudad de firma pendiente]"),
            "org.serviceArea", new Resolver(Organization::getServiceArea, "[Zona de cobertura pendiente]")
    );

    private final OrganizationRepository organizationRepository;

    /** Renders {@code template} against the single active organization. */
    public String render(String template) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        return render(template, organizationRepository.findSingleton());
    }

    /** Pure rendering against a given organization ({@code null} = every org value pending). */
    public static String render(String template, Organization organization) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            Resolver resolver = RESOLVERS.get(key);
            String replacement;
            if (BRAND_PLACEHOLDER.equals(key)) {
                replacement = BRAND_NAME;
            } else if (resolver != null) {
                replacement = resolver.resolve(organization);
            } else {
                replacement = matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private record Resolver(Function<Organization, String> extractor, String fallback) {
        String resolve(Organization organization) {
            String value = organization == null ? null : extractor.apply(organization);
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }
}
