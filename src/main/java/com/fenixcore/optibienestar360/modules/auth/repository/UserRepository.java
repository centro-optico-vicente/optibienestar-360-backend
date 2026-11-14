package com.fenixcore.optibienestar360.modules.auth.repository;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUuid(UUID uuid);

    /** Internal id of a user by its public uuid — one column, no entity load (inbox polling). */
    @Query("SELECT u.id FROM User u WHERE u.uuid = :uuid")
    Optional<Long> findIdByUuid(@Param("uuid") UUID uuid);

    /**
     * Readable name of a user for document metadata ("Generado por"): the person's full name, or
     * the login email when the name is blank. One column, no entity load.
     */
    @Query("SELECT COALESCE(NULLIF(TRIM(p.fullName), ''), u.email) FROM User u JOIN u.person p WHERE u.uuid = :uuid")
    Optional<String> findDisplayNameByUuid(@Param("uuid") UUID uuid);

    boolean existsByEmail(String email);

    /** A person already holding a login (ally request, hub ADR 0036). */
    boolean existsByPersonId(Long personId);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.role.permissions"})
    Optional<User> findByEmailAndActiveTrue(String email);

    /**
     * Unlike {@link #findByEmailAndActiveTrue}, this doesn't filter by
     * {@code active} — {@code AuthService.login()} needs to distinguish
     * "email not found" from "account inactive" for {@code login_audit_log}
     * ({@code FAILED_INACTIVE}, spec 16-audit.md §Login), even though both
     * still surface the same generic auth error to the caller.
     */
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.role.permissions"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.role.permissions"})
    Optional<User> findWithRolesByUuid(UUID uuid);

    Optional<User> findByPasswordResetToken(String token);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.role.permissions"})
    Optional<User> findByLoginTokenHash(String tokenHash);

    /**
     * Login by national ID: {@code persons} holds the document, {@code users.person_id}
     * is unique, so a (type, number) pair resolves to at most one account.
     */
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.role.permissions"})
    Optional<User> findByPerson_DocumentTypeAndPerson_DocumentNumber(String documentType, String documentNumber);

    /**
     * Clears the sign-in role of every user whose default is {@code roleId}
     * (used when the role is deactivated: nobody may keep starting with it).
     * Returns how many users changed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.defaultRole = null WHERE u.defaultRole.id = :roleId")
    int clearDefaultRole(@Param("roleId") Long roleId);

    /**
     * Login emails of the active users holding {@code permission} through an
     * active role assignment (internal reminders to whoever approves
     * payments, hub ADR 0038 §9).
     */
    @Query("""
            SELECT DISTINCT u.email FROM User u
              JOIN u.userRoles ur
              JOIN ur.role r
              JOIN r.permissions p
            WHERE p.name = :permission
              AND u.active = true
              AND ur.active = true
              AND u.email IS NOT NULL
            """)
    java.util.List<String> findActiveEmailsWithPermission(@Param("permission") String permission);
}
