package com.fenixcore.optibienestar360.modules.terms.web;

import com.fenixcore.optibienestar360.core.exception.ForbiddenDetailsException;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        interceptor = new TermsGateInterceptor(termsAcceptanceService, TermType.ADVISOR);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingContract_isRefusedWithItsType() {
        authenticate();
        when(termsAcceptanceService.hasPending(userUuid, TermType.ADVISOR)).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preHandle(get(), new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ForbiddenDetailsException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo(TermsGateInterceptor.PENDING_CODE);
                    assertThat(ex.getProperties()).containsEntry("termType", "ASESOR");
                });
    }

    @Test
    void acceptedContract_passes() {
        authenticate();
        when(termsAcceptanceService.hasPending(userUuid, TermType.ADVISOR)).thenReturn(false);

        assertThat(interceptor.preHandle(get(), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void anonymousRequest_isLeftToSpringSecurity() {
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
