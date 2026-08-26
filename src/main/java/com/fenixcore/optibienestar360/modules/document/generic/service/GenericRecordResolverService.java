package com.fenixcore.optibienestar360.modules.document.generic.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GenericRecordResolverService {

    private final EntityManager entityManager;

    public GenericRecordResolverService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Resolves an entity instance from database by table or entity name
     * and identifier (UUID, numeric ID, code, etc.).
     */
    @Transactional(readOnly = true)
    public Object findRecordByTableAndId(String entityOrTable, String identifier) {
        if (entityOrTable == null || entityOrTable.isBlank()) {
            throw new IllegalArgumentException("Entity or table name must not be blank");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Record identifier must not be blank");
        }

        EntityType<?> matchingEntityType = findMatchingEntityType(entityOrTable);
        if (matchingEntityType == null) {
            throw new NoSuchElementException("No entity or table found matching: '" + entityOrTable + "'");
        }

        Class<?> entityClass = matchingEntityType.getJavaType();
        String entityName = matchingEntityType.getName();

        // 1. Try finding by 'uuid' attribute if identifier is a valid UUID and entity has 'uuid'
        if (isUuid(identifier) && hasAttribute(matchingEntityType, "uuid")) {
            try {
                List<?> results = entityManager.createQuery(
                        "SELECT e FROM " + entityName + " e WHERE e.uuid = :uuid", entityClass
                )
                .setParameter("uuid", UUID.fromString(identifier))
                .setMaxResults(1)
                .getResultList();

                if (!results.isEmpty()) {
                    return results.get(0);
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Try finding by primary key (@Id)
        try {
            Object parsedId = parseId(identifier, matchingEntityType);
            if (parsedId != null) {
                Object entity = entityManager.find(entityClass, parsedId);
                if (entity != null) {
                    return entity;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Fallback: Try finding by common business identifier fields ('code', 'identifier', 'cedula', 'username')
        for (String field : List.of("code", "identifier", "cedula", "username")) {
            if (hasAttribute(matchingEntityType, field)) {
                try {
                    List<?> results = entityManager.createQuery(
                            "SELECT e FROM " + entityName + " e WHERE e." + field + " = :val", entityClass
                    )
                    .setParameter("val", identifier)
                    .setMaxResults(1)
                    .getResultList();

                    if (!results.isEmpty()) {
                        return results.get(0);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        throw new NoSuchElementException("Could not find record with identifier '" + identifier + "' in table/entity '" + entityOrTable + "'");
    }

    /**
     * Resolves records for a given table with safety checks and tenant/actor filters.
     */
    @Transactional(readOnly = true)
    public List<?> findRecordsByTable(String targetTable, int limit) {
        return findRecordsByTable(targetTable, limit, null);
    }

    @Transactional(readOnly = true)
    public List<?> findRecordsByTable(String targetTable, int limit, UUID actorUuid) {
        return findRecordsByTable(targetTable, limit, actorUuid, null, false);
    }

    @Transactional(readOnly = true)
    public List<?> findRecordsByTable(String targetTable, int limit, UUID actorUuid, String q, boolean includeInactive) {
        if (targetTable == null || targetTable.isBlank()) {
            throw new IllegalArgumentException("Target table must not be blank");
        }

        EntityType<?> matchingEntityType = findMatchingEntityType(targetTable);
        if (matchingEntityType == null) {
            throw new NoSuchElementException("No table found matching: '" + targetTable + "'");
        }

        Class<?> entityClass = matchingEntityType.getJavaType();
        String entityName = matchingEntityType.getName();
        String simpleClassName = entityClass.getSimpleName();
        int maxResults = limit > 0 ? limit : 500;
        boolean actorIsSystem = isSystemActor(actorUuid);
        String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : null;

        StringBuilder hql = new StringBuilder("SELECT e FROM ").append(entityName).append(" e WHERE 1=1");

        // 1. Filter active records if entity has 'active' property, unless the caller asked to include inactive ones
        if (!includeInactive && hasAttribute(matchingEntityType, "active")) {
            hql.append(" AND e.active = true");
        }

        // 2. Free-text search on the entity's 'name' attribute, if present
        if (trimmedQ != null && hasAttribute(matchingEntityType, "name")) {
            hql.append(" AND LOWER(e.name) LIKE LOWER(:q)");
        }

        // 3. Security for 'users' table: Hide users with SYSTEM role if actor is not SYSTEM
        if (simpleClassName.equalsIgnoreCase("User")) {
            if (!actorIsSystem) {
                hql.append(" AND NOT EXISTS (SELECT ur FROM UserRole ur WHERE ur.user = e AND ur.active = true AND ur.role.name = 'SYSTEM')");
            }
        }

        // 4. Security for 'roles' table: Hide SYSTEM role if actor is not SYSTEM
        if (simpleClassName.equalsIgnoreCase("Role")) {
            if (!actorIsSystem) {
                hql.append(" AND e.name != 'SYSTEM'");
            }
        }

        var typedQuery = entityManager.createQuery(hql.toString(), entityClass)
                .setMaxResults(maxResults);
        if (trimmedQ != null && hasAttribute(matchingEntityType, "name")) {
            typedQuery.setParameter("q", "%" + trimmedQ + "%");
        }

        return typedQuery.getResultList();
    }

    private boolean isSystemActor(UUID actorUuid) {
        if (actorUuid == null) return false;
        try {
            List<?> results = entityManager.createQuery(
                    "SELECT ur FROM UserRole ur WHERE ur.user.uuid = :uuid AND ur.active = true AND ur.role.name = 'SYSTEM'"
            )
            .setParameter("uuid", actorUuid)
            .setMaxResults(1)
            .getResultList();

            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private EntityType<?> findMatchingEntityType(String targetTable) {
        if (targetTable == null || targetTable.isBlank()) return null;
        String cleanTarget = targetTable.trim();

        for (EntityType<?> entityType : entityManager.getMetamodel().getEntities()) {
            Table tableAnno = entityType.getJavaType().getAnnotation(Table.class);
            String tableName = (tableAnno != null && !tableAnno.name().isBlank())
                    ? tableAnno.name()
                    : entityType.getName();

            if (tableName.equalsIgnoreCase(cleanTarget)) {
                return entityType;
            }
        }
        return null;
    }

    private boolean isUuid(String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasAttribute(EntityType<?> entityType, String attributeName) {
        try {
            return entityType.getAttribute(attributeName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private Object parseId(String identifier, EntityType<?> entityType) {
        try {
            Class<?> idType = entityType.getIdType().getJavaType();
            if (idType.equals(Long.class) || idType.equals(long.class)) {
                return Long.parseLong(identifier);
            } else if (idType.equals(UUID.class)) {
                return UUID.fromString(identifier);
            } else if (idType.equals(Integer.class) || idType.equals(int.class)) {
                return Integer.parseInt(identifier);
            } else if (idType.equals(String.class)) {
                return identifier;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
