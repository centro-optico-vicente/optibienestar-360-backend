package com.fenixcore.optibienestar360.modules.card;

import com.fenixcore.optibienestar360.modules.card.dto.DigitalCardDto;
import com.fenixcore.optibienestar360.modules.card.service.DigitalCardService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@link MyDigitalCardController} — {@code GET /v1/me/digital-card} is
 * gated by {@code MEMBER_VIEW_OWN}; the not-enrolled 404 is surfaced from the
 * mocked service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MyDigitalCardControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private DigitalCardService digitalCardService;

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

    private DigitalCardDto card() {
        return new DigitalCardDto(UUID.randomUUID(), "Ana Pérez", "V", "12345678",
                "Familiar", "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 1, 15),
                "data:image/png;base64,QR");
    }

    @Test
    void anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/digital-card"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/me/digital-card").with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void withPermission_is200() throws Exception {
        when(digitalCardService.getForUser(any())).thenReturn(card());

        mockMvc.perform(get("/v1/me/digital-card").with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Pérez"))
                .andExpect(jsonPath("$.qrCodeDataUri").value("data:image/png;base64,QR"));
    }

    @Test
    void notEnrolled_is404() throws Exception {
        when(digitalCardService.getForUser(any()))
                .thenThrow(new NoSuchElementException("me.member.not_enrolled"));

        mockMvc.perform(get("/v1/me/digital-card").with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isNotFound());
    }
}
