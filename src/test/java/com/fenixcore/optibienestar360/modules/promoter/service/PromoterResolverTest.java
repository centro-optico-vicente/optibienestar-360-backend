package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromoterResolver} — the enrollment attribution rule
 * (v2 PDF #4/#5): promoter-table precedence by code, INSTITUCION fallback.
 */
@ExtendWith(MockitoExtension.class)
class PromoterResolverTest {

    @Mock private PromoterRepository promoterRepository;
    @InjectMocks private PromoterResolver resolver;

    @Test
    void resolvesToActivePromoter_whenCodeMatches() {
        Promoter real = promoter("VICENTE", true);
        when(promoterRepository.findByReferralCode("VICENTE")).thenReturn(Optional.of(real));

        assertThat(resolver.resolveForEnrollment("VICENTE")).isSameAs(real);
    }

    @Test
    void normalizesCode_trimAndUppercase() {
        Promoter real = promoter("VICENTE", true);
        when(promoterRepository.findByReferralCode("VICENTE")).thenReturn(Optional.of(real));

        // Lowercased + padded input still resolves — codes are stored UPPER.
        assertThat(resolver.resolveForEnrollment("  vicente ")).isSameAs(real);
    }

    @Test
    void fallsBackToInstitucion_whenCodeIsBlank() {
        Promoter institucion = promoter("INSTITUCION", true);
        when(promoterRepository.findByReferralCode("INSTITUCION")).thenReturn(Optional.of(institucion));

        assertThat(resolver.resolveForEnrollment("   ")).isSameAs(institucion);
        assertThat(resolver.resolveForEnrollment(null)).isSameAs(institucion);
    }

    @Test
    void fallsBackToInstitucion_whenCodeIsUnknown() {
        when(promoterRepository.findByReferralCode("NOPE")).thenReturn(Optional.empty());
        Promoter institucion = promoter("INSTITUCION", true);
        when(promoterRepository.findByReferralCode("INSTITUCION")).thenReturn(Optional.of(institucion));

        assertThat(resolver.resolveForEnrollment("NOPE")).isSameAs(institucion);
    }

    @Test
    void fallsBackToInstitucion_whenMatchedPromoterIsInactive() {
        Promoter inactive = promoter("VICENTE", false);
        when(promoterRepository.findByReferralCode("VICENTE")).thenReturn(Optional.of(inactive));
        Promoter institucion = promoter("INSTITUCION", true);
        when(promoterRepository.findByReferralCode("INSTITUCION")).thenReturn(Optional.of(institucion));

        assertThat(resolver.resolveForEnrollment("VICENTE")).isSameAs(institucion);
    }

    @Test
    void returnsNull_whenInstitucionSeedMissing() {
        // Broken deploy: no code and no INSTITUCION → null (logged, never throws,
        // so enrollment is not blocked).
        lenient().when(promoterRepository.findByReferralCode("INSTITUCION")).thenReturn(Optional.empty());

        assertThat(resolver.resolveForEnrollment(null)).isNull();
    }

    private Promoter promoter(String code, boolean active) {
        Promoter p = new Promoter();
        p.setId(1L);
        p.setUuid(UUID.randomUUID());
        p.setReferralCode(code);
        p.setDisplayName(code);
        p.setActive(active);
        return p;
    }
}
