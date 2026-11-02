package com.fenixcore.optibienestar360.modules.ally.dto;

/**
 * A VERIFIED license of a published ally's holder person, as the public
 * directory shows it (V213): issuing body + number — public trust data in
 * Venezuela (the M.P.P.S. and Colegio numbers are printed on prescriptions
 * and office signs). Pending or rejected licenses are never public.
 */
public record PublicAllyLicenseDto(
        String licensingBodyCode,
        String licensingBodyName,
        String licenseNumber
) {}
