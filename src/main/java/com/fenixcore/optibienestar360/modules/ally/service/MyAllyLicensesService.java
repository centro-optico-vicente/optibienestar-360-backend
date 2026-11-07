package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.LicensingBodyRepository;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The professional ally's own licenses in the portal (hub ADR 0036 §5): a
 * self-registered professional loads them while staff review the request.
 * Same rules as the admin side ({@link AllyLicensesService}: they hang from
 * the holder person, a number is unique per issuing body), behind the ally
 * membership — any member reads, the OWNER adds or removes. A license staff
 * already verified can only be removed by staff.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyAllyLicensesService {

    private final AllyUserRepository allyUserRepository;
    private final AllyLicensesService licensesService;
    private final LicensingBodyRepository licensingBodyRepository;

    public List<AllyLicenseDto> list(UUID allyUuid, UUID actorUserUuid) {
        requireMembership(allyUuid, actorUserUuid, false);
        return licensesService.list(allyUuid);
    }

    /** Issuing bodies the form offers (active only). */
    public List<DisplayRef> bodies(UUID allyUuid, UUID actorUserUuid) {
        requireMembership(allyUuid, actorUserUuid, false);
        return licensingBodyRepository.findAllByActiveTrueOrderByName().stream().map(DisplayRefs::ref).toList();
    }

    @Transactional
    public AllyLicenseDto add(UUID allyUuid, UUID actorUserUuid, AllyLicenseRequest req) {
        requireMembership(allyUuid, actorUserUuid, true);
        return licensesService.add(allyUuid, req);
    }

    @Transactional
    public void delete(UUID allyUuid, UUID licenseUuid, UUID actorUserUuid) {
        requireMembership(allyUuid, actorUserUuid, true);
        boolean verified = licensesService.list(allyUuid).stream()
                .anyMatch(l -> l.uuid().equals(licenseUuid) && l.verificationStatus() == VerificationStatus.VERIFIED);
        if (verified) {
            throw new IllegalArgumentException("person_license.verified_locked");
        }
        licensesService.delete(allyUuid, licenseUuid);
    }

    private void requireMembership(UUID allyUuid, UUID actorUserUuid, boolean ownerOnly) {
        AllyUser membership = actorUserUuid == null ? null
                : allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, actorUserUuid).orElse(null);
        if (membership == null || (ownerOnly && membership.getAllyRole() != AllyRole.OWNER)) {
            throw new AccessDeniedException("ally.profile.not_allowed");
        }
    }
}
