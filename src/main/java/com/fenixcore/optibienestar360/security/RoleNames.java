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

    /** Granted on self-service affiliation. */
    public static final String MEMBER = "AFILIADO";

    /** Sales advisor (internally {@code promoter}). */
    public static final String ADVISOR = "ASESOR";

    private RoleNames() {
    }
}
