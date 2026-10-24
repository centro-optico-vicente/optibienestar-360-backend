package com.fenixcore.optibienestar360.modules.terms.web;

import com.fenixcore.optibienestar360.core.exception.ForbiddenDetailsException;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Blocks a self-service module while its contract is pending: registered by
 * {@code TermsGateConfig} on {@code /v1/advisors/me/**} (ASESOR) and
 * {@code /v1/allies/me/**} (ALIADO), one instance per contract kind. A
 * caller with a pending version of that kind gets 403 {@code terms.pending}
 * with {@code termType}, so the frontend can offer the contract instead of a
 * generic error. MEMBER is not gated here: it is mandatory and the frontend
 * modal can't be dismissed while it is pending.
 */
@RequiredArgsConstructor
public class TermsGateInterceptor implements HandlerInterceptor {

    public static final String PENDING_CODE = "terms.pending";

    private final TermsAcceptanceService termsAcceptanceService;
    private final TermType termType;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails actor)) {
            // Unauthenticated requests are Spring Security's business, not ours.
            return true;
        }
        if (termsAcceptanceService.hasPending(actor.getUuid(), termType)) {
            throw new ForbiddenDetailsException(PENDING_CODE, Map.of("termType", termType.code()));
        }
        return true;
    }
}
