package com.fenixcore.optibienestar360.core.branding;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Resolves the brand identity snapshot (name, legal name, RIF, colors, footer,
 * and direct S3 logo URL) for generated documents (Thymeleaf HTML/PDF, JasperReports).
 *
 * <p>Logo resolution chain:
 * <ol>
 *   <li>Organization's custom logo direct URL in S3/R2 (via {@code organizations.logo_key})</li>
 *   <li>Classpath default fallback ({@link BrandAssets#logo()} or {@link BrandAssets#logoNegative()})</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
public class BrandingService {

    private final OrganizationRepository organizationRepository;
    private final ObjectProvider<StorageService> storageProvider;
    private final SystemConfigService systemConfigService;

    public BrandingService(
            OrganizationRepository organizationRepository,
            ObjectProvider<StorageService> storageProvider,
            SystemConfigService systemConfigService
    ) {
        this.organizationRepository = organizationRepository;
        this.storageProvider = storageProvider;
        this.systemConfigService = systemConfigService;
    }

    public BrandingContext getContext() {
        Organization org = null;
        try {
            org = organizationRepository.findSingleton();
        } catch (Exception e) {
            log.debug("No organization found: {}", e.getMessage());
        }

        String displayName = (org != null && org.getName() != null && !org.getName().isBlank())
                ? org.getName().trim()
                : "OptiBienestar 360";
        String legalName = org != null ? org.getLegalName() : null;
        String taxId = org != null ? org.getTaxIdentifier() : null;

        String logoUrl = resolveLogoUrl(org);
        byte[] logoPng = BrandAssets.logo();
        byte[] logoNegativePng = BrandAssets.logoNegative();
        byte[] isotypePng = BrandAssets.isotype();
        // If an S3 logoUrl is present, templates use the direct URL; otherwise fallback to classpath data URI
        String logoDataUri = logoUrl == null ? BrandingContext.toDataUri(logoPng) : null;

        String footer = resolveFooter(org);

        return new BrandingContext(
                displayName,
                legalName,
                taxId,
                logoUrl,
                logoPng,
                logoNegativePng,
                isotypePng,
                logoDataUri,
                BrandPalette.DEFAULT,
                footer
        );
    }

    /**
     * Resolves the direct S3 URL (public URL or presigned URL) for the organization's logo.
     * Returns null if no custom logo is configured or storage is unavailable.
     */
    public String resolveLogoUrl(Organization org) {
        if (org == null || org.getLogoKey() == null || org.getLogoKey().isBlank()) {
            return null;
        }

        String key = org.getLogoKey().trim();
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }

        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.warn("StorageService unavailable to resolve URL for logo key: {}", key);
            return null;
        }

        String publicBase = storage.getPublicBaseUrl();
        if (publicBase != null && !publicBase.isBlank()) {
            return publicBase.endsWith("/") ? publicBase + key : publicBase + "/" + key;
        }

        // Generate presigned URL (1 hour expiration) as direct link
        try {
            return storage.generatePresignedUrl(storage.getPublicBucket(), key, Duration.ofHours(1));
        } catch (Exception e) {
            try {
                return storage.generatePresignedUrl(storage.getBucket(), key, Duration.ofHours(1));
            } catch (Exception ex) {
                log.warn("Could not generate direct URL for logo key {}: {}", key, ex.getMessage());
                return null;
            }
        }
    }

    /**
     * Resolves the direct URL (or classpath location) to pass as parameter to JasperReports.
     *
     * @param forDarkBackground if true and using default brand, points to the negative logo
     */
    public String getLogoUrl(boolean forDarkBackground) {
        Organization org = null;
        try {
            org = organizationRepository.findSingleton();
        } catch (Exception ignored) {}

        String s3Url = resolveLogoUrl(org);
        if (s3Url != null && !s3Url.isBlank()) {
            return s3Url;
        }

        return forDarkBackground ? BrandAssets.LOGO_NEGATIVE : BrandAssets.LOGO_HORIZONTAL;
    }

    /**
     * Compatibility hook for when a logo is updated/deleted.
     */
    public void clearLogoCache() {
        log.debug("Logo cache cleared");
    }

    private String resolveFooter(Organization org) {
        String configuredFooter = systemConfigService != null ? systemConfigService.getReportFooter() : null;
        if (configuredFooter != null && !configuredFooter.isBlank() && !SystemConfigService.DEFAULT_REPORT_FOOTER.equals(configuredFooter)) {
            return configuredFooter;
        }

        if (org == null) {
            return "OptiBienestar 360";
        }

        StringBuilder sb = new StringBuilder();
        if (org.getName() != null && !org.getName().isBlank()) {
            sb.append(org.getName().trim());
        }
        if (org.getLegalName() != null && !org.getLegalName().isBlank()) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(org.getLegalName().trim());
        }
        if (org.getTaxIdentifier() != null && !org.getTaxIdentifier().isBlank()) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append("RIF ").append(org.getTaxIdentifier().trim());
        }

        return !sb.isEmpty() ? sb.toString() : "OptiBienestar 360";
    }
}
