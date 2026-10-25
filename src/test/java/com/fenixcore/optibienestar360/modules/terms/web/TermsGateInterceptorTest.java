package com.fenixcore.optibienestar360.modules.terms.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TermsGateInterceptor}: 403 {@code terms.pending} (with the
 * {@code termType} code) while the caller has a pending contract of the
 * gated kind; pass-through once accepted or without an authenticated user.
 */
@ExtendWith(MockitoExtension.class)
class TermsGateInterceptorTest {

    @Mock private TermsAcceptanceService termsAcceptanceService;

    private TermsGateInterceptor interceptor;
    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        interceptor = new TermsGateInterceptor(termsAcceptanceService, TermType.ADVISOR, messageSource,
                new ObjectMapper(), "https://problems.test");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingContract_isRefusedWithItsType() throws Exception {
        authenticate();
        when(termsAcceptanceService.hasPending(userUuid, TermType.ADVISOR)).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(get(), response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains(TermsGateInterceptor.PENDING_CODE)
                .contains("ASESOR");
    }

    @Test
    void acceptedContract_passes() throws Exception {
        authenticate();
        when(termsAcceptanceService.hasPending(userUuid, TermType.ADVISOR)).thenReturn(false);

        assertThat(interceptor.preHandle(get(), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void anonymousRequest_isLeftToSpringSecurity() throws Exception {
        assertThat(interceptor.preHandle(get(), new MockHttpServletResponse(), new Object())).isTrue();
        verify(termsAcceptanceService, never()).hasPending(any(), any());
    }

    private MockHttpServletRequest get() {
        return new MockHttpServletRequest("GET", "/v1/advisors/me");
    }

    private void authenticate() {
        CustomUserDetails actor = new CustomUserDetails(1L, userUuid, "jti", "es", "a@b.c", "", List.of(), true);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
    }
}
