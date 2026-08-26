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
        String normalizedTarget = cleanTarget.toLowerCase().replace("-", "_");

        for (EntityType<?> entityType : entityManager.getMetamodel().getEntities()) {
            Class<?> javaType = entityType.getJavaType();
            Table tableAnno = javaType.getAnnotation(Table.class);
            String tableName = (tableAnno != null && !tableAnno.name().isBlank())
                    ? tableAnno.name()
                    : entityType.getName();
            String className = javaType.getSimpleName();

            // 1. Direct case-insensitive match on table name or class simple name
            if (tableName.equalsIgnoreCase(cleanTarget) || className.equalsIgnoreCase(cleanTarget)) {
                return entityType;
            }

            // 2. Normalized snake_case match (supports kebab-case e.g. scheduled-jobs -> scheduled_jobs)
            String normalizedTable = tableName.toLowerCase().replace("-", "_");
            String normalizedClass = className.toLowerCase().replace("-", "_");
            if (normalizedTarget.equals(normalizedTable) || normalizedTarget.equals(normalizedClass)) {
                return entityType;
            }

            // 3. Plural / Singular and variation match (e.g. ally <-> allies, country <-> countries, city <-> cities)
            if (matchesPluralOrSingular(normalizedTarget, normalizedTable, normalizedClass)) {
                return entityType;
            }
        }
        return null;
    }

    private boolean matchesPluralOrSingular(String target, String tableName, String className) {
        // e.g. ally vs allies, country vs countries, city vs cities
        if (target.endsWith("y") && (tableName.equals(target.substring(0, target.length() - 1) + "ies") || className.equalsIgnoreCase(target.substring(0, target.length() - 1) + "ies"))) {
            return true;
        }
        if (target.endsWith("ies") && (tableName.equals(target.substring(0, target.length() - 3) + "y") || className.equalsIgnoreCase(target.substring(0, target.length() - 3) + "y"))) {
            return true;
        }
        // e.g. user vs users, member vs members, plan vs plans, role vs roles
        if (tableName.equals(target + "s") || className.equalsIgnoreCase(target + "s") || target.equals(tableName + "s") || target.equalsIgnoreCase(className + "s")) {
            return true;
        }
        // e.g. status vs statuses
        if (tableName.equals(target + "es") || className.equalsIgnoreCase(target + "es") || target.equals(tableName + "es") || target.equalsIgnoreCase(className + "es")) {
            return true;
        }
        // Stripped underscores comparison (e.g. allytype vs ally_types, scheduledjob vs scheduled_jobs)
        String noUnderTarget = target.replace("_", "");
        String noUnderTable = tableName.replace("_", "");
        String noUnderClass = className.toLowerCase().replace("_", "");
        return noUnderTarget.equals(noUnderTable)
                || noUnderTarget.equals(noUnderClass)
                || (noUnderTarget + "s").equals(noUnderTable)
                || (noUnderTarget + "es").equals(noUnderTable)
                || (noUnderTable + "s").equals(noUnderTarget)
                || (noUnderTable + "es").equals(noUnderTarget);
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
