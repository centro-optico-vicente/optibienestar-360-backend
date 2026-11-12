package com.fenixcore.optibienestar360.modules.document.generic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import com.fenixcore.optibienestar360.modules.document.generic.service.GenericEntityExtractorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The extractor's Jackson attempt must stop at the first cyclic reference (bidirectional JPA relations)
 * instead of walking the graph until the nesting limit, and must keep serializing acyclic graphs as before.
 */
class GenericEntityExtractorCycleGuardTest {

    private static final AtomicInteger PARENT_GETTER_CALLS = new AtomicInteger();

    private GenericEntityExtractorService extractorService;

    public static class CyclicParent {
        private final String name;
        private final List<CyclicChild> children = new ArrayList<>();

        public CyclicParent(String name) { this.name = name; }

        public String getName() { return name; }
        public List<CyclicChild> getChildren() { return children; }
    }

    public static class CyclicChild {
        private final String description;
        private final Integer quantity;
        private final CyclicParent parent;

        public CyclicChild(String description, Integer quantity, CyclicParent parent) {
            this.description = description;
            this.quantity = quantity;
            this.parent = parent;
        }

        public String getDescription() { return description; }
        public Integer getQuantity() { return quantity; }
        public CyclicParent getParent() {
            PARENT_GETTER_CALLS.incrementAndGet();
            return parent;
        }
    }

    public static class SharedCatalog {
        private final String name;

        public SharedCatalog(String name) { this.name = name; }

        public String getName() { return name; }
    }

    public static class DiamondHolder {
        private final String code;
        private final SharedCatalog origin;
        private final SharedCatalog destination;

        public DiamondHolder(String code, SharedCatalog origin, SharedCatalog destination) {
            this.code = code;
            this.origin = origin;
            this.destination = destination;
        }

        public String getCode() { return code; }
        public SharedCatalog getOrigin() { return origin; }
        public SharedCatalog getDestination() { return destination; }
    }

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        extractorService = new GenericEntityExtractorService(mapper, null);
        PARENT_GETTER_CALLS.set(0);
    }

    @Test
    @DisplayName("A bidirectional graph renders through the reflection fallback without walking the cycle")
    void cyclicGraphStopsAtFirstRepeatedAncestor() {
        CyclicParent parent = new CyclicParent("Clínica Central");
        parent.getChildren().add(new CyclicChild("Consulta", 1, parent));
        parent.getChildren().add(new CyclicChild("Lentes", 2, parent));

        GenericRecordModel model = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> extractorService.extractModel(parent, "Ficha", null, "X-1", "tester"));

        assertTrue(model.fields().containsValue("Clínica Central"), "parent scalar field is rendered");
        assertEquals(1, model.detailSections().size(), "children are rendered as one detail table");
        GenericRecordModel.DetailSection section = model.detailSections().get(0);
        assertEquals(2, section.rows().size());
        assertTrue(section.rows().stream().anyMatch(row -> row.contains("Consulta")));
        assertTrue(section.rows().stream().anyMatch(row -> row.contains("Lentes")));

        // Unguarded, Jackson calls getParent() hundreds of times per attempt before its nesting limit fails it.
        assertTrue(PARENT_GETTER_CALLS.get() < 50,
                "cycle must be cut early, getParent() was called " + PARENT_GETTER_CALLS.get() + " times");
    }

    @Test
    @DisplayName("The same object referenced twice without a cycle still serializes")
    void sharedReferenceIsNotACycle() {
        SharedCatalog city = new SharedCatalog("Caracas");
        DiamondHolder holder = new DiamondHolder("R-7", city, city);

        GenericRecordModel model = extractorService.extractModel(holder, "Ficha", null, "R-7", "tester");

        long caracasCount = model.fields().values().stream().filter("Caracas"::equals).count();
        assertEquals(2, caracasCount, "both references to the shared object are rendered");
        assertTrue(model.fields().containsValue("R-7"));
    }
}
