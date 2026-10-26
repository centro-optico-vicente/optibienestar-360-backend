package com.fenixcore.optibienestar360.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test (ADR LOC-0015): for three reference DTOs, the generated
 * OpenAPI schema must describe exactly what the real response mapper writes —
 * same property names ({@code _Display} pairs included), every present key
 * listed in {@code required}, and {@code null} only where the schema is
 * nullable. If someone changes {@code DisplayValueSerializerModifier} without
 * updating {@code WireContractModelConverter}, this test fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class OpenApiWireFidelityIT {

    @Autowired
    private WebApplicationContext context;

    /** The mapper Spring MVC uses to serialize HTTP responses. */
    @Autowired
    private JsonMapper jsonMapper;

    private Map<String, Object> spec;
    private Map<String, Object> wire;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String raw = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        spec = jsonMapper.readValue(raw, Map.class);
        wire = jsonMapper.readValue(jsonMapper.writeValueAsString(WireSamples.all()), Map.class);
    }

    @Test
    void allyListItem_hasDisplayPairsInsteadOfNestedRef() {
        String name = WireSamples.ALLY_LIST_ITEM;
        assertMatchesWire(name);

        Map<String, Object> properties = properties(name);
        assertFalse(properties.containsKey("city"), "FK object must be flattened");
        for (String key : List.of("city_Uuid", "city_Display", "city_Code", "published_Display",
                "publishedAt_Display", "active_Display", "status_Display", "createdAt_Display")) {
            assertTrue(properties.containsKey(key), "missing " + key);
        }
        assertTrue(required(name).contains("city_Uuid"));
        assertTrue(required(name).contains("createdAt_Display"));
        assertFalse(required(name).contains("city_Code"), "_Code is only emitted when the ref has a code");

        assertTrue(isNullable(property(name, "name")), "reference component without @NotNull is nullable");
        assertFalse(isNullable(property(name, "published")), "primitive is never null");
        assertFalse(isNullable(property(name, "published_Display")), "_Display of a primitive is never null");
        assertTrue(isNullable(property(name, "createdAt_Display")));
    }

    @Test
    void memberDetail_isRequiredAndNullableLikeTheWire() {
        String name = WireSamples.MEMBER_DETAIL;
        assertMatchesWire(name);

        assertTrue(isNullable(property(name, "uuid")));
        assertTrue(isNullable(property(name, "city")), "nested object may be null");
        assertFalse(isNullable(property(name, "activeBeneficiariesCount")));
        assertFalse(isNullable(property(name, "hasMedicalRecord")));
        assertTrue(required(name).contains("activeBeneficiariesCount"));
    }

    @Test
    void publicAllyDetail_isRequiredAndNullableLikeTheWire() {
        String name = WireSamples.PUBLIC_ALLY_DETAIL;
        assertMatchesWire(name);

        assertTrue(isNullable(property(name, "services")));
        assertTrue(required(name).contains("services"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonNullInclusionDto_isOptionalAndBaseValuesAreNotNullable() {
        String name = WireSamples.COMMISSION_NON_NULL;
        Map<String, Object> sample = (Map<String, Object>) wire.get(name);
        Map<String, Object> properties = properties(name);
        Set<String> required = new HashSet<>(required(name));

        // Keys are omitted when null, so the schema must list them, but as optional and non-null.
        assertTrue(properties.keySet().containsAll(sample.keySet()), "every written key is in the schema");
        assertTrue(properties.containsKey("promoter_Uuid"), "FK flattened in a NON_NULL DTO too");
        assertTrue(sample.size() < properties.size(), "the blank sample omits its null keys");
        for (String key : properties.keySet()) {
            if (!key.endsWith("_Display") && !key.endsWith("_Uuid")) {
                // Base values are omitted when null. (_Uuid/_Display stay nullable on purpose:
                // the formatter may write an explicit null label for a present value.)
                assertFalse(isNullable((Map<String, Object>) properties.get(key)), key + " is omitted, never null");
            }
            assertEquals(sample.containsKey(key), required.contains(key),
                    key + " is required exactly when the server always writes it");
        }
    }

    /** Names, presence and nullability of the schema against the serialized sample. */
    @SuppressWarnings("unchecked")
    private void assertMatchesWire(String name) {
        Map<String, Object> sample = (Map<String, Object>) wire.get(name);
        assertNotNull(sample, "no wire sample for " + name);
        Map<String, Object> properties = properties(name);
        Set<String> required = new HashSet<>(required(name));

        assertEquals(sample.keySet(), properties.keySet(), "property names of " + name);

        for (Map.Entry<String, Object> entry : sample.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> property = (Map<String, Object>) properties.get(key);
            if (!key.endsWith("_Code")) {
                assertTrue(required.contains(key), key + " is written by the server so it must be required");
            }
            if (entry.getValue() == null) {
                assertTrue(isNullable(property), key + " is null on the wire so it must be nullable");
            }
        }
        for (String key : required) {
            assertTrue(sample.containsKey(key), key + " is required but the server does not write it");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schema(String name) {
        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
        assertNotNull(schema, "schema " + name + " not in spec");
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(String name) {
        return (Map<String, Object>) schema(name).get("properties");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> property(String name, String property) {
        Map<String, Object> found = (Map<String, Object>) properties(name).get(property);
        assertNotNull(found, name + "." + property + " not in spec");
        return found;
    }

    @SuppressWarnings("unchecked")
    private List<String> required(String name) {
        Object required = schema(name).get("required");
        return required == null ? List.of() : (List<String>) required;
    }

    /** OpenAPI 3.1: {@code type} array with {@code "null"}, or {@code anyOf} with a null branch. */
    @SuppressWarnings("unchecked")
    private static boolean isNullable(Map<String, Object> property) {
        Object type = property.get("type");
        if (type instanceof List<?> types && types.contains("null")) {
            return true;
        }
        if ("null".equals(type) || Boolean.TRUE.equals(property.get("nullable"))) {
            return true;
        }
        Object anyOf = property.get("anyOf");
        if (anyOf instanceof List<?> branches) {
            for (Object branch : branches) {
                if (branch instanceof Map<?, ?> b && isNullable((Map<String, Object>) b)) {
                    return true;
                }
            }
        }
        return false;
    }

}
