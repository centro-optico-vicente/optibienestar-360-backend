package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord.EmergencyContactRelationship;
import jakarta.validation.constraints.Size;

/**
 * Body of the emergency-contact {@code PUT}s. Replace semantics: the three
 * fields are written as sent, so a {@code null} (or blank) clears it. Sizes
 * mirror the V19 columns.
 */
public record EmergencyContactUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 30) String phone,
        EmergencyContactRelationship relationship
) {}
