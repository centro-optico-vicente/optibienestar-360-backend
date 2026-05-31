package com.fenixcore.optisaludplus.core.config;

import com.fenixcore.optisaludplus.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * i18n wiring per ADR 0010 (Venezuela primary market) and the i18n vertical
 * plan: app default {@code es-VE}, supported {@code es} + {@code en}, and a
 * hybrid locale resolver that — once user-level locale lands in
 * {@code CustomUserDetails} (Fase 4) — prefers the JWT {@code locale} claim
 * over the {@code Accept-Language} header.
 *
 * <p>This config is the Fase 1 deliverable: the {@code MessageSource} comes
 * from Spring Boot's autoconfig ({@code spring.messages.basename}), and
 * {@link LocalValidatorFactoryBean} is wired to it so {@code @Pattern(message
 * = "{code}")} on DTOs resolves codes through the bundle chain instead of
 * defaulting to {@code ValidationMessages.properties}.</p>
 */
@Configuration
public class I18nConfig {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es-VE");

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("en")
    );

    /**
     * Routes Jakarta Bean Validation messages through the same
     * {@code MessageSource} used everywhere else, so {@code @Pattern(message =
     * "{validation.code.uppercase.range}")} resolves against
     * {@code ValidationMessages_es.properties} / {@code _en.properties} based
     * on the current request locale.
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }

    /**
     * Hybrid resolver, evaluated for every incoming request by Spring MVC.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li><b>JWT {@code locale} claim</b> (Fase 4) — read from
     *       {@code CustomUserDetails.locale} when the request is authenticated.
     *       Wins when present; reflects the user's explicit profile choice.</li>
     *   <li><b>{@code Accept-Language} header</b> — matched against the
     *       supported-locales whitelist ({@code es}, {@code en}). Variants
     *       like {@code es-MX} or {@code en-GB} match through Java's
     *       BCP47 language-range matching and resolve to the base bundle.</li>
     *   <li><b>App default</b> {@code es-VE}.</li>
     * </ol>
     *
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new HybridLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }

    /**
     * Extends Spring's {@link AcceptHeaderLocaleResolver} so the JWT
     * {@code locale} claim — surfaced through {@link CustomUserDetails} by
     * {@code JwtAuthenticationFilter} — wins over the {@code Accept-Language}
     * header. Falls back to the parent's header/whitelist logic when no
     * authenticated principal is present (login, recover-password, public
     * endpoints) or when the claim is blank.
     */
    static final class HybridLocaleResolver extends AcceptHeaderLocaleResolver {

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            String claim = readLocaleClaim();
            if (claim != null && !claim.isBlank()) {
                Locale parsed = Locale.forLanguageTag(claim);
                if (parsed != null && !parsed.getLanguage().isEmpty()) {
                    return parsed;
                }
            }
            return super.resolveLocale(request);
        }

        private String readLocaleClaim() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof CustomUserDetails user) {
                return user.getLocale();
            }
            return null;
        }
    }
}
