package com.fenixcore.optibienestar360.security;

/**
 * Names of the seeded roles ({@code roles.name}). The constants are English;
 * the values are the Spanish domain codes stored in the database (V6, role
 * ASESOR renamed to ASESOR in V185 per the business glossary). Also the
 * value carried by the JWT {@code role_name} claim.
 */
public final class RoleNames {

    /** Superuser; the only role allowed to manage other SYSTEM users and the catalogs. */
    public static final String SYSTEM = "SYSTEM";

    public static final String ADMINISTRATOR = "ADMINISTRADOR";

    public static final String OPERATOR = "OPERADOR";

    /** Medical operator working on the ally side. */
    public static final String ALLY_MEDICAL_OPERATOR = "OPERADOR_MEDICO";

    public static final String ALLY = "ALIADO";
    /** Self-registered ally whose request is still under review (V230, hub ADR 0036): own profile only. */
    public static final String ALLY_PENDING = "ALIADO_PENDIENTE";

    /** Granted on self-service affiliation. */
    public static final String MEMBER = "AFILIADO";

    /** Self-registered affiliate whose registration is in progress or in review (V233, hub ADR 0037): own registration only. */
    public static final String MEMBER_PENDING = "AFILIADO_PENDIENTE";

    /** Sales advisor (internally {@code promoter}). */
    public static final String ADVISOR = "ASESOR";

    private RoleNames() {
    }
}
