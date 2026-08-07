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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromoterResolver} — the enrollment attribution rule
 * (v2 PDF #4/#5): promoter-table lookup by code only, {@code null} otherwise
 * (no forced INSTITUCION default — project chat 2026-08-06: a member can now
 * be enrolled without a promoter and linked later via
 * {@code POST /v1/admin/members/{uuid}/assign-promoter}).
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
    void returnsNull_whenCodeIsBlankOrAbsent() {
        assertThat(resolver.resolveForEnrollment("   ")).isNull();
        assertThat(resolver.resolveForEnrollment(null)).isNull();
        verify(promoterRepository, never()).findByReferralCode(anyString());
    }

    @Test
    void returnsNull_whenCodeIsUnknown() {
        when(promoterRepository.findByReferralCode("NOPE")).thenReturn(Optional.empty());

        assertThat(resolver.resolveForEnrollment("NOPE")).isNull();
    }

    @Test
    void returnsNull_whenMatchedPromoterIsInactive() {
        Promoter inactive = promoter("VICENTE", false);
        when(promoterRepository.findByReferralCode("VICENTE")).thenReturn(Optional.of(inactive));

        assertThat(resolver.resolveForEnrollment("VICENTE")).isNull();
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
