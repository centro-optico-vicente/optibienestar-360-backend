package com.fenixcore.optibienestar360.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Generates the OpenAPI contract without a database and without a human
 * (ADR LOC-0015): boots the same Spring context the other ITs use (H2, Flyway
 * off), calls {@code GET /v3/api-docs} through MockMvc and writes
 * {@code build/openapi/openapi.json}. CI uploads that file as an artifact (and
 * {@code publish.yaml} attaches it to each release).
 *
 * <p>It also writes {@code build/openapi/wire-samples.json}: what the real
 * response serializer (the Spring Boot Jackson 3 mapper, with the
 * {@code _Display} modifier) emits for sample instances of three reference
 * DTOs, so spec and wire can be compared side by side.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class OpenApiContractIT {

    static final Path OUTPUT_DIR = Path.of("build", "openapi");

    @Autowired
    private WebApplicationContext context;

    /** The mapper Spring MVC uses to serialize HTTP responses. */
    @Autowired
    private JsonMapper jsonMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void writesOpenApiJson() throws Exception {
        String raw = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode spec = jsonMapper.readTree(raw);
        assertTrue(spec.has("openapi"), "spec must declare its OpenAPI version");
        assertTrue(spec.path("paths").size() > 0, "spec must contain paths");

        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve("openapi.json"),
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n",
                StandardCharsets.UTF_8);
    }

    @Test
    void writesWireSamples() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve("wire-samples.json"),
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(WireSamples.all()) + "\n",
                StandardCharsets.UTF_8);
    }

}
