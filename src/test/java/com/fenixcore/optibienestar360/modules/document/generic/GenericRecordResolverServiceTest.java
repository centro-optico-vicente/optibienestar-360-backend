package com.fenixcore.optibienestar360.modules.document.generic;

import com.fenixcore.optibienestar360.modules.document.generic.service.GenericRecordResolverService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GenericRecordResolverServiceTest {

    private EntityManager entityManager;
    private Metamodel metamodel;
    private EntityType dummyEntityType;
    private GenericRecordResolverService resolverService;

    @jakarta.persistence.Table(name = "members")
    public static class DummyMember {
        private UUID uuid;
        private Long id;

        public DummyMember(UUID uuid, Long id) {
            this.uuid = uuid;
            this.id = id;
        }

        public UUID getUuid() { return uuid; }
        public Long getId() { return id; }
    }

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        metamodel = mock(Metamodel.class);
        dummyEntityType = mock(EntityType.class);

        when(entityManager.getMetamodel()).thenReturn(metamodel);
        when(dummyEntityType.getName()).thenReturn("Member");
        when(dummyEntityType.getJavaType()).thenReturn((Class) DummyMember.class);

        Type idType = mock(Type.class);
        when(idType.getJavaType()).thenReturn((Class) Long.class);
        when(dummyEntityType.getIdType()).thenReturn(idType);

        when(metamodel.getEntities()).thenReturn((Set) Set.of(dummyEntityType));

        resolverService = new GenericRecordResolverService(entityManager);
    }

    @Test
    @DisplayName("Should resolve entity by UUID when table name matches exactly")
    void testFindRecordByUuid() {
        UUID testUuid = UUID.randomUUID();
        DummyMember dummy = new DummyMember(testUuid, 100L);

        when(dummyEntityType.getAttribute("uuid")).thenReturn(mock(jakarta.persistence.metamodel.Attribute.class));

        TypedQuery query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(DummyMember.class))).thenReturn(query);
        when(query.setParameter(eq("uuid"), eq(testUuid))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(dummy));

        Object found = resolverService.findRecordByTableAndId("members", testUuid.toString());

        assertNotNull(found);
        assertTrue(found instanceof DummyMember);
        assertEquals(testUuid, ((DummyMember) found).getUuid());
    }

    @Test
    @DisplayName("Should fetch records list from an existing table")
    void testFindRecordsByTable() {
        DummyMember dummy = new DummyMember(UUID.randomUUID(), 100L);
        TypedQuery query = mock(TypedQuery.class);

        when(entityManager.createQuery(anyString(), eq(DummyMember.class))).thenReturn(query);
        when(query.setMaxResults(50)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(dummy));

        List<?> list = resolverService.findRecordsByTable("members", 50);

        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("Should throw NoSuchElementException if table or entity does not exist")
    void testNotFoundEntity() {
        assertThrows(NoSuchElementException.class, () ->
                resolverService.findRecordByTableAndId("non_existent_table", "123")
        );
    }
}
