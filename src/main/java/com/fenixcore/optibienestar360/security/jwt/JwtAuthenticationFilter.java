package com.fenixcore.optibienestar360.security.jwt;

import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.modules.auth.service.TokenBlacklistService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final LoginAuditService loginAuditService;

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtService.isValid(token) && jwtService.isAccessToken(token)) {
            String jti = jwtService.extractJti(token);

            if (blacklistService.isBlacklisted(jti)) {
                log.debug("Blacklisted token rejected: jti={}", jti);
            } else {
                String subject = jwtService.extractSubject(token);
                long iat = jwtService.extractIssuedAt(token);
                long userEpoch = blacklistService.getUserInvalidatedEpoch(subject);

                if (iat < userEpoch) {
                    // Token was issued before this user's permissions/roles
                    // were last edited. Force the client to refresh so it
                    // picks up the new claims.
                    log.debug("Stale token rejected by user epoch: subject={} iat={} epoch={}",
                            subject, iat, userEpoch);
                } else {
                    UUID sessionId = jwtService.extractSessionId(token);

                    if (!loginAuditService.isSessionValid(sessionId)) {
                        log.debug("Rejected token for a closed/expired session: sid={}", sessionId);
                    } else {
                        List<String> permissions = jwtService.extractPermissions(token);
                        String locale = jwtService.extractLocale(token);

                        List<SimpleGrantedAuthority> authorities = permissions.stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();

                        CustomUserDetails principal = CustomUserDetails.fromJwt(
                                UUID.fromString(subject), jti, locale, sessionId, authorities);

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
