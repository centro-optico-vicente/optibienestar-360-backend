package com.fenixcore.optibienestar360.modules.promoter.entity;

/**
 * Codes of the seeded advisor ranks ({@code promoter_ranks.code}, V101). The
 * constants are English; the values are the Spanish domain codes stored in
 * the database (base rank PROMOTOR renamed to ASESOR in V185 per the business
 * glossary). Ranks are an admin catalog — only the base rank is referenced by
 * code at runtime; the hierarchy itself is driven by {@code hierarchy_level}.
 */
public final class AdvisorRankCodes {

    /** Base rank every new advisor starts at. */
    public static final String ADVISOR = "ASESOR";

    public static final String SUPERVISOR = "SUPERVISOR";

    public static final String COORDINATOR = "COORDINADOR";

    private AdvisorRankCodes() {
    }
}
