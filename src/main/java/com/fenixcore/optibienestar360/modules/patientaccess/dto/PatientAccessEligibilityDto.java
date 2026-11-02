package com.fenixcore.optibienestar360.modules.patientaccess.dto;

/**
 * Whether the caller can use the patient medical history at an ally (hub ADR
 * 0032): {@code enabled} is the system switch, {@code eligible} says whether
 * this caller at this ally qualifies (B2P ally of the MEDICAL area, and the
 * caller is its holder person or an OPERADOR_MEDICO). The frontend shows the
 * "Ver historia médica" button only when both are true.
 */
public record PatientAccessEligibilityDto(boolean enabled, boolean eligible) {}
