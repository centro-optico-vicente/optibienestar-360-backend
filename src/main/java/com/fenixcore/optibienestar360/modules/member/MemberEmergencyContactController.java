package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.EmergencyContactDto;
import com.fenixcore.optibienestar360.modules.member.dto.EmergencyContactUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.service.MemberEmergencyContactService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The titular's emergency contact, split out of the medical record so the people
 * who load the titular's data can fill it without seeing clinical data (V212,
 * hub ADR 0031 §14). Three surfaces, one per audience:
 *
 * <ul>
 *   <li>{@code /v1/admin/members/{memberUuid}/emergency-contact} — staff, any member.</li>
 *   <li>{@code /v1/me/advisor/portfolio/members/{memberUuid}/emergency-contact} — advisor,
 *       own portfolio only (secondary path {@code /v1/advisors/me/portfolio/...}).</li>
 *   <li>{@code /v1/me/member/emergency-contact} — the affiliate's own record.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class MemberEmergencyContactController {

    private final MemberEmergencyContactService service;

    // ─── Staff ──────────────────────────────────────────────────────────────

    @GetMapping("/v1/admin/members/{memberUuid}/emergency-contact")
    @PreAuthorize("hasAnyAuthority('MEMBER_EMERGENCY_CONTACT_VIEW_ALL', 'MEMBER_EMERGENCY_CONTACT_UPDATE', 'MEDICAL_RECORD_VIEW')")
    public ResponseEntity<EmergencyContactDto> get(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(service.get(memberUuid));
    }

    @PutMapping("/v1/admin/members/{memberUuid}/emergency-contact")
    @PreAuthorize("hasAnyAuthority('MEMBER_EMERGENCY_CONTACT_UPDATE', 'MEDICAL_RECORD_UPDATE')")
    public ResponseEntity<EmergencyContactDto> update(@PathVariable UUID memberUuid,
                                                      @Valid @RequestBody EmergencyContactUpdateRequest request) {
        return ResponseEntity.ok(service.update(memberUuid, request));
    }

    // ─── Advisor (own portfolio) ────────────────────────────────────────────

    @GetMapping({"/v1/me/advisor/portfolio/members/{memberUuid}/emergency-contact",
                 "/v1/advisors/me/portfolio/members/{memberUuid}/emergency-contact"})
    @PreAuthorize("hasAuthority('MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE')")
    public ResponseEntity<EmergencyContactDto> getForAdvisor(@PathVariable UUID memberUuid,
                                                             @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.getForAdvisor(actor.getUuid(), memberUuid));
    }

    @PutMapping({"/v1/me/advisor/portfolio/members/{memberUuid}/emergency-contact",
                 "/v1/advisors/me/portfolio/members/{memberUuid}/emergency-contact"})
    @PreAuthorize("hasAuthority('MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE')")
    public ResponseEntity<EmergencyContactDto> updateForAdvisor(@PathVariable UUID memberUuid,
                                                                @Valid @RequestBody EmergencyContactUpdateRequest request,
                                                                @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.updateForAdvisor(actor.getUuid(), memberUuid, request));
    }

    // ─── Affiliate (own record) ─────────────────────────────────────────────

    @GetMapping("/v1/me/member/emergency-contact")
    @PreAuthorize("hasAuthority('MEMBER_EMERGENCY_CONTACT_VIEW_OWN')")
    public ResponseEntity<EmergencyContactDto> getOwn(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.getOwn(actor.getUuid()));
    }

    @PutMapping("/v1/me/member/emergency-contact")
    @PreAuthorize("hasAuthority('MEMBER_EMERGENCY_CONTACT_UPDATE_OWN')")
    public ResponseEntity<EmergencyContactDto> updateOwn(@Valid @RequestBody EmergencyContactUpdateRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.updateOwn(actor.getUuid(), request));
    }
}
