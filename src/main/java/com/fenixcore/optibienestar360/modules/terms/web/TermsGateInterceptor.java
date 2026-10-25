package com.fenixcore.optibienestar360.modules.terms.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URI;

/**
 * Blocks a self-service module while its contract is pending: registered by
 * {@code TermsGateConfig} on every path of the advisor and ally portals, one
 * instance per contract kind. A caller with a pending version of that kind
 * gets 403 {@code terms.pending} with {@code code} and {@code termType}, so
 * the frontend can offer the contract instead of a generic error. MEMBER is
 * not gated here: when mandatory, the frontend modal can't be dismissed.
 *
 * <p>The problem body is written straight to the response (same as
 * {@code JwtAccessDeniedHandler}) rather than thrown: the gate runs before
 * the handler's own permission / not-found logic and must not interfere with
 * it — an unauthenticated caller, or one with no user row, passes through.</p>
 */
@RequiredArgsConstructor
public class TermsGateInterceptor implements HandlerInterceptor {

    public static final String PENDING_CODE = "terms.pending";

    private final TermsAcceptanceService termsAcceptanceService;
    private final TermType termType;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final String problemsBase;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails actor)) {
            // Unauthenticated requests are Spring Security's business, not ours.
            return true;
        }
        if (!termsAcceptanceService.hasPending(actor.getUuid(), termType)) return true;

        var locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(403);
        problem.setType(URI.create(problemsBase + "/forbidden"));
        problem.setTitle(messageSource.getMessage("error.title.forbidden", null, "Forbidden", locale));
        problem.setDetail(messageSource.getMessage(PENDING_CODE, null, PENDING_CODE, locale));
        problem.setProperty("code", PENDING_CODE);
        problem.setProperty("termType", termType.code());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
        return false;
    }
}
