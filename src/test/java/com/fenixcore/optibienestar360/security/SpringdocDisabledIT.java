package com.fenixcore.optibienestar360.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code SPRINGDOC_ENABLED=false} (the {@code prod} default), the spec and
 * the Swagger UI must not leak: SecurityConfig stops permitting those paths,
 * so anonymous callers get 401 instead of the document or a redirect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class SpringdocDisabledIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void apiDocs_isNotServedAnonymously() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUi_isNotServedAnonymously() throws Exception {
        mockMvc.perform(get("/swagger-ui"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayPublic() throws Exception {
        mockMvc.perform(get("/v1/system-info"))
                .andExpect(status().is2xxSuccessful());
    }

}
