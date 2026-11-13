package com.fenixcore.optibienestar360.modules.terms.web;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The per-request question {@link TermsGateInterceptor} asks: does this user have
 * terms of {@code type} pending? Answering it takes 5–6 queries
 * ({@link TermsAcceptanceService#hasPending}: user, current versions, promoter /
 * ally links, agreements, acceptances), and the gate asks it on every request
 * to the advisor and ally portals.
 *
 * <p>Only the "nothing pending" answer is cached (cache {@code terms-gate},
 * 60 s TTL in {@code RedisCacheConfig}, key = user + term type). A "pending"
 * answer is never cached, so a user who accepts the terms is let through on the
 * very next request without any eviction, and the gate can never block someone
 * because of a stale entry. A new obligation (new terms version, new agreement)
 * reaches users already let through within the TTL.</p>
 *
 * <p>Caching is only enabled in the {@code prod} profile ({@code @EnableCaching}
 * lives in {@code RedisCacheConfig}); elsewhere this is a plain pass-through.</p>
 */
@Component
@RequiredArgsConstructor
public class TermsGateCheck {

    public static final String CACHE = "terms-gate";

    private final TermsAcceptanceService termsAcceptanceService;

    @Cacheable(value = CACHE, key = "#userUuid.toString() + ':' + #type.name()", unless = "#result")
    public boolean hasPending(UUID userUuid, TermType type) {
        return termsAcceptanceService.hasPending(userUuid, type);
    }
}
