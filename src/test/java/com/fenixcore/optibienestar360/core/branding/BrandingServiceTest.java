package com.fenixcore.optibienestar360.core.branding;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandingServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ObjectProvider<StorageService> storageProvider;

    @Mock
    private StorageService storageService;

    @Mock
    private SystemConfigService systemConfigService;

    private BrandingService brandingService;

    @BeforeEach
    void setUp() {
        brandingService = new BrandingService(organizationRepository, storageProvider, systemConfigService);
    }

    @Test
    @DisplayName("When no organization is found, falls back to default brand name, classpath logo, and default footer")
    void getContext_whenNoOrg_returnsClasspathDefaults() {
        when(organizationRepository.findSingleton()).thenThrow(new IllegalStateException("organization.not_configured"));

        BrandingContext ctx = brandingService.getContext();

        assertThat(ctx.displayName()).isEqualTo("OptiBienestar 360");
        assertThat(ctx.legalName()).isNull();
        assertThat(ctx.taxId()).isNull();
        assertThat(ctx.logoUrl()).isNull();
        assertThat(ctx.logoPng()).isEqualTo(BrandAssets.logo());
        assertThat(ctx.logoNegativePng()).isEqualTo(BrandAssets.logoNegative());
        assertThat(ctx.logoDataUri()).startsWith("data:image/png;base64,");
        assertThat(ctx.footer()).isEqualTo("OptiBienestar 360");
    }

    @Test
    @DisplayName("When organization exists but has no logoKey, uses org details and default classpath logo")
    void getContext_whenOrgHasNoLogoKey_usesOrgDetailsAndDefaultLogo() {
        Organization org = new Organization();
        org.setName("Centro Óptico Vicente");
        org.setLegalName("Óptica Vicente C.A.");
        org.setTaxIdentifier("J-12345678-9");
        org.setLogoKey(null);
        when(organizationRepository.findSingleton()).thenReturn(org);

        BrandingContext ctx = brandingService.getContext();

        assertThat(ctx.displayName()).isEqualTo("Centro Óptico Vicente");
        assertThat(ctx.legalName()).isEqualTo("Óptica Vicente C.A.");
        assertThat(ctx.taxId()).isEqualTo("J-12345678-9");
        assertThat(ctx.logoUrl()).isNull();
        assertThat(ctx.logoDataUri()).isNotNull();
        assertThat(ctx.footer()).isEqualTo("Centro Óptico Vicente · Óptica Vicente C.A. · RIF J-12345678-9");
    }

    @Test
    @DisplayName("When organization has logoKey in S3, resolves direct public URL")
    void getContext_whenOrgHasLogoKey_resolvesDirectPublicUrl() {
        Organization org = new Organization();
        org.setName("Acme Corp");
        org.setLogoKey("public/organizations/test/logo.png");
        when(organizationRepository.findSingleton()).thenReturn(org);

        when(storageProvider.getIfAvailable()).thenReturn(storageService);
        when(storageService.getPublicBaseUrl()).thenReturn("https://storage.cdn.com/optibienestar-public");

        BrandingContext ctx = brandingService.getContext();

        assertThat(ctx.displayName()).isEqualTo("Acme Corp");
        assertThat(ctx.logoUrl()).isEqualTo("https://storage.cdn.com/optibienestar-public/public/organizations/test/logo.png");
        assertThat(ctx.logoDataUri()).isNull(); // When direct URL is present, no heavy base64 dataUri is generated
    }

    @Test
    @DisplayName("When organization has logoKey and no publicBaseUrl, resolves presigned direct URL")
    void getContext_whenNoPublicBaseUrl_generatesPresignedUrl() {
        Organization org = new Organization();
        org.setName("Acme Corp");
        org.setLogoKey("organizations/private/logo.png");
        when(organizationRepository.findSingleton()).thenReturn(org);

        when(storageProvider.getIfAvailable()).thenReturn(storageService);
        when(storageService.getPublicBaseUrl()).thenReturn(null);
        when(storageService.getPublicBucket()).thenReturn("optibienestar-public");
        when(storageService.generatePresignedUrl(eq("optibienestar-public"), eq("organizations/private/logo.png"), any(Duration.class)))
                .thenReturn("https://s3.amazonaws.com/optibienestar-public/logo.png?X-Amz-Signature=123");

        BrandingContext ctx = brandingService.getContext();

        assertThat(ctx.displayName()).isEqualTo("Acme Corp");
        assertThat(ctx.logoUrl()).isEqualTo("https://s3.amazonaws.com/optibienestar-public/logo.png?X-Amz-Signature=123");
    }

    @Test
    @DisplayName("getLogoUrl returns direct S3 URL when configured or classpath asset when not")
    void getLogoUrl_returnsAppropriateLocation() {
        // Without S3 logo -> classpath assets
        when(organizationRepository.findSingleton()).thenReturn(null);
        assertThat(brandingService.getLogoUrl(true)).isEqualTo(BrandAssets.LOGO_NEGATIVE);
        assertThat(brandingService.getLogoUrl(false)).isEqualTo(BrandAssets.LOGO_HORIZONTAL);

        // With S3 logo -> direct S3 URL
        Organization org = new Organization();
        org.setLogoKey("https://cdn.example.com/logo.png");
        when(organizationRepository.findSingleton()).thenReturn(org);

        assertThat(brandingService.getLogoUrl(true)).isEqualTo("https://cdn.example.com/logo.png");
    }
}
