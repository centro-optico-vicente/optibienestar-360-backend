package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord.EmergencyContactRelationship;

/**
 * The titular's emergency contact — ONLY the three emergency-contact columns of
 * the person's medical record (V19). It never carries clinical data, so it can
 * be served to operators, advisors and the affiliate, who must not see the rest
 * of the medical record (V212, hub ADR 0031 §14).
 *
 * <p>{@code exists} is {@code false} while no contact has been loaded (empty-state
 * convention, ADR 0012: 200 with a flag, never 404).</p>
 */
public record EmergencyContactDto(
        String name,
        String phone,
        @Display(Display.Kind.ENUM) EmergencyContactRelationship relationship,
        boolean exists
) {
    public static EmergencyContactDto empty() {
        return new EmergencyContactDto(null, null, null, false);
    }
}
