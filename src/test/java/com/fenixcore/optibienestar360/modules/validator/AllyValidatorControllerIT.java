package com.fenixcore.optibienestar360.modules.validator;

import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.modules.validator.dto.ValidationResultDto;
import com.fenixcore.optibienestar360.modules.validator.dto.ValidationResultDto.ValidationStatus;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the ally validator path ({@link AllyValidatorController}):
 * {@code GET /v1/me/allies/validations/{document}} gated on
 * {@code ALLY_VALIDATE_MEMBER}, plus the secondary {@code /v1/allies/me/validations}
 * path answering with the same security. {@link ValidatorService} is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AllyValidatorControllerIT {

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private ValidatorService validatorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private ValidationResultDto notFound() {
        return new ValidationResultDto(ValidationStatus.NOT_FOUND, "V", "12345678",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, false);
    }

    @Test
    void validate_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/allies/validations/{d}", "V-12345678"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validate_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/me/allies/validations/{d}", "V-12345678").with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validate_withPermission_is200() throws Exception {
        when(validatorService.validate(anyString())).thenReturn(notFound());

        mockMvc.perform(get("/v1/me/allies/validations/{d}", "V-12345678").with(principal("ALLY_VALIDATE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    // ─── Secondary path /v1/allies/me/validations (kept, not deprecated) ─────

    @Test
    void secondaryAlliesMePath_answersLikePrimary_withSameSecurity() throws Exception {
        when(validatorService.validate(anyString())).thenReturn(notFound());

        mockMvc.perform(get("/v1/allies/me/validations/{d}", "V-12345678"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/allies/me/validations/{d}", "V-12345678").with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/allies/me/validations/{d}", "V-12345678").with(principal("ALLY_VALIDATE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }
}
