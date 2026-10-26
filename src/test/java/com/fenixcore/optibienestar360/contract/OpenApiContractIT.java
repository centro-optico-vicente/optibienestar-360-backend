package com.fenixcore.optibienestar360.contract;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyDetailDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("AllyListItemDto", new AllyListItemDto(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Optica Sample", 7,
                List.of("Optica"),
                new DisplayRef(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), "CCS", "Caracas"),
                "J", "123456789", null, null,
                true, Instant.parse("2026-01-02T03:04:05Z"),
                true, "APPROVED", Instant.parse("2026-01-01T00:00:00Z")));
        samples.put("AppliedSortPage(empty)", new AppliedSortPage<String>(
                new PageImpl<String>(List.of(), PageRequest.of(0, 10), 0), List.of()));
        samples.put("MemberDetailDto", RecordSamples.blank(MemberDetailDto.class));
        samples.put("PublicAllyDetailDto", RecordSamples.blank(PublicAllyDetailDto.class));

        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve("wire-samples.json"),
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(samples) + "\n",
                StandardCharsets.UTF_8);
    }

    /** Builds a record with default component values (null / 0 / false). */
    static final class RecordSamples {

        private RecordSamples() {
        }

        static Object blank(Class<?> recordType) throws Exception {
            RecordComponent[] components = recordType.getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                values[i] = defaultOf(types[i]);
            }
            Constructor<?> ctor = recordType.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(values);
        }

        private static Object defaultOf(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == double.class) {
                return 0.0d;
            }
            if (type == float.class) {
                return 0.0f;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == char.class) {
                return (char) 0;
            }
            return 0;
        }
    }

}
