package com.fenixcore.optibienestar360.core.config;

import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link I18nConfig.HybridLocaleResolver} — covers the three
 * fallback levels and the whitelist behaviour:
 * <ol>
 *     <li>JWT claim {@code locale} (via {@link CustomUserDetails}) wins.</li>
 *     <li>{@code Accept-Language} header (filtered by supported-locales).</li>
 *     <li>App default {@code es-VE}.</li>
 * </ol>
 */
class HybridLocaleResolverTest {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es-VE");
    private static final List<Locale> SUPPORTED = List.of(
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("en")
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private I18nConfig.HybridLocaleResolver newResolver() {
        I18nConfig.HybridLocaleResolver resolver = new I18nConfig.HybridLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED);
        return resolver;
    }

    private MockHttpServletRequest request(String acceptLanguage) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            req.addHeader("Accept-Language", acceptLanguage);
        }
        return req;
    }

    private void authenticate(String localeClaim) {
        CustomUserDetails principal = CustomUserDetails.fromJwt(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                localeClaim,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
        );
    }

    // ─── Level 2 + 3: sin autenticación, manda el header (con whitelist) ────

    @Test
    void no_auth_header_in_whitelist_returns_header_locale() {
        Locale resolved = newResolver().resolveLocale(request("en"));
        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void no_auth_header_outside_whitelist_falls_to_default() {
        // pt-BR no está en supportedLocales → cae al default app es-VE.
        Locale resolved = newResolver().resolveLocale(request("pt-BR"));
        assertThat(resolved).isEqualTo(DEFAULT_LOCALE);
    }

    @Test
    void no_auth_no_header_falls_to_default() {
        Locale resolved = newResolver().resolveLocale(request(null));
        assertThat(resolved).isEqualTo(DEFAULT_LOCALE);
    }

    // ─── Level 1: claim JWT gana sobre el header ────────────────────────────

    @Test
    void jwt_claim_wins_over_accept_language_header() {
        authenticate("en");
        Locale resolved = newResolver().resolveLocale(request("es-VE"));
        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void null_claim_in_principal_falls_to_header() {
        authenticate(null);
        Locale resolved = newResolver().resolveLocale(request("en"));
        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void blank_claim_in_principal_falls_to_header() {
        authenticate("   ");
        Locale resolved = newResolver().resolveLocale(request("en"));
        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void empty_string_claim_in_principal_falls_to_header() {
        authenticate("");
        Locale resolved = newResolver().resolveLocale(request("en"));
        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    // ─── Documented asymmetry: el claim NO pasa por la whitelist ────────────

    @Test
    void claim_outside_whitelist_is_returned_as_is_without_filtering() {
        // El resolver NO filtra el claim contra supportedLocales — la única defensa
        // contra valores arbitrarios viene de @Pattern en LocalePreferenceRequest
        // cuando el usuario lo setea desde POST /v1/me/locale. Si el claim trae
        // 'pt-BR' (por algún path raro), el resolver lo devuelve igual y el
        // MessageSource cae a messages.properties (EN canónico) por su propio
        // fallback de ResourceBundle.
        authenticate("pt-BR");
        Locale resolved = newResolver().resolveLocale(request("es"));
        assertThat(resolved).isEqualTo(Locale.forLanguageTag("pt-BR"));
    }
}
