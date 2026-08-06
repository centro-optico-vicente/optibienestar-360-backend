package com.fenixcore.optibienestar360.core.dto;

import java.util.UUID;

/** Lightweight projection for select/dropdown options — {@code code} is {@code null} when the entity has no own code. */
public record OptionDto(UUID uuid, String code, String label, boolean active) {}
