package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseVerifyRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyLicenseDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.LicensingBody;
import com.fenixcore.optibienestar360.modules.catalog.repository.LicensingBodyRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense.VerificationStatus;
import com.fenixcore.optibienestar360.modules.person.repository.PersonProfessionalLicenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Professional licenses of an ally's holder person (V213 / hub ADR 0031 §5),
 * mounted at {@code /v1/admin/allies/{allyUuid}/licenses}. The license
 * belongs to the person, so every site of the same professional (one ally per
 * site) shows the same licenses. Every read/write resolves the ally from the
 * path and checks the license belongs to its holder person (404 on URL
 * tampering). Delete is a soft-delete.
 *
 * <p>Any change of body or number sends the license back to {@code PENDING}:
 * a verification only covers what was actually checked.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyLicensesService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

    private final AllyRepository allyRepository;
    private final LicensingBodyRepository licensingBodyRepository;
    private final PersonProfessionalLicenseRepository licenseRepository;

    /** Empty when the ally has no holder person yet (a B2B ally or a B2P still missing it). */
    public List<AllyLicenseDto> list(UUID allyUuid) {
        Person holder = findAlly(allyUuid).getHolderPerson();
        if (holder == null) {
            return List.of();
        }
        return licenseRepository.findByPerson_IdAndActiveTrue(holder.getId(), DEFAULT_SORT).stream()
                .map(AllyLicensesService::toDto)
                .toList();
    }

    /**
     * Verified licenses of a publicly visible ally (active + published), for
     * the public directory. 404 otherwise, same as the public detail.
     */
    public List<PublicAllyLicenseDto> publicList(UUID allyUuid) {
        Ally ally = allyRepository.findByUuid(allyUuid)
                .filter(Ally::isActive)
                .filter(Ally::isPublished)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
        Person holder = ally.getHolderPerson();
        if (holder == null) {
            return List.of();
        }
        return licenseRepository.findByPerson_IdAndActiveTrueAndVerificationStatus(
                        holder.getId(), VerificationStatus.VERIFIED, DEFAULT_SORT).stream()
                .map(l -> new PublicAllyLicenseDto(l.getLicensingBody().getCode(),
                        l.getLicensingBody().getName(), l.getLicenseNumber()))
                .toList();
    }

    @Transactional
    @Auditable(entity = "person_professional_license", action = AuditAction.CREATE)
    public AllyLicenseDto add(UUID allyUuid, AllyLicenseRequest req) {
        Person holder = requireHolder(findAlly(allyUuid));
        LicensingBody body = findBody(req.licensingBodyUuid());
        String number = req.licenseNumber().trim();
        ensureNumberFree(body, number);

        PersonProfessionalLicense license = new PersonProfessionalLicense();
        license.setPerson(holder);
        license.setLicensingBody(body);
        license.setLicenseNumber(number);
        return toDto(licenseRepository.save(license));
    }

    @Transactional
    @Auditable(entity = "person_professional_license", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public AllyLicenseDto update(UUID allyUuid, UUID licenseUuid, AllyLicenseRequest req) {
        PersonProfessionalLicense license = findUnderAlly(allyUuid, licenseUuid);
        LicensingBody body = findBody(req.licensingBodyUuid());
        String number = req.licenseNumber().trim();
        boolean changed = !body.getId().equals(license.getLicensingBody().getId())
                || !number.equalsIgnoreCase(license.getLicenseNumber());
        if (changed) {
            ensureNumberFree(body, number);
            license.setLicensingBody(body);
            license.setLicenseNumber(number);
            resetVerification(license);
        }
        return toDto(license);  // managed → dirty-check on commit
    }

    @Transactional
    @Auditable(entity = "person_professional_license", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public AllyLicenseDto verify(UUID allyUuid, UUID licenseUuid, AllyLicenseVerifyRequest req, UUID actorUuid) {
        if (req.status() == VerificationStatus.PENDING) {
            throw new IllegalArgumentException("person_license.verification.invalid");
        }
        PersonProfessionalLicense license = findUnderAlly(allyUuid, licenseUuid);
        license.setVerificationStatus(req.status());
        license.setVerifiedBy(actorUuid);
        license.setVerifiedAt(Instant.now());
        return toDto(license);
    }

    @Transactional
    @Auditable(entity = "person_professional_license", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void delete(UUID allyUuid, UUID licenseUuid) {
        PersonProfessionalLicense license = findUnderAlly(allyUuid, licenseUuid);
        license.setActive(false);
        license.setStatus("INACTIVE");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Ally findAlly(UUID allyUuid) {
        return allyRepository.findByUuid(allyUuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    private LicensingBody findBody(UUID uuid) {
        return licensingBodyRepository.findByUuid(uuid)
                .filter(LicensingBody::isActive)
                .orElseThrow(() -> new NoSuchElementException("licensing_body.not_found"));
    }

    /** Licenses hang from the holder person: without one there is nobody to license. */
    private static Person requireHolder(Ally ally) {
        if (ally.getHolderPerson() == null) {
            throw new IllegalArgumentException("person_license.holder_required");
        }
        return ally.getHolderPerson();
    }

    /** Pre-checks the V213 partial unique index for a clean 422 instead of a 409. */
    private void ensureNumberFree(LicensingBody body, String number) {
        if (licenseRepository.existsByLicensingBody_IdAndLicenseNumberIgnoreCaseAndActiveTrue(body.getId(), number)) {
            throw new IllegalArgumentException("person_license.number.duplicate");
        }
    }

    /** Soft-deleted licenses, or licenses of another person, are treated as gone (404). */
    private PersonProfessionalLicense findUnderAlly(UUID allyUuid, UUID licenseUuid) {
        Person holder = findAlly(allyUuid).getHolderPerson();
        PersonProfessionalLicense license = licenseRepository.findByUuid(licenseUuid)
                .filter(PersonProfessionalLicense::isActive)
                .orElseThrow(() -> new NoSuchElementException("person_license.not_found"));
        if (holder == null || !license.getPerson().getId().equals(holder.getId())) {
            throw new NoSuchElementException("person_license.not_found");
        }
        return license;
    }

    private static void resetVerification(PersonProfessionalLicense license) {
        license.setVerificationStatus(VerificationStatus.PENDING);
        license.setVerifiedBy(null);
        license.setVerifiedAt(null);
    }

    static AllyLicenseDto toDto(PersonProfessionalLicense l) {
        LicensingBody body = l.getLicensingBody();
        return new AllyLicenseDto(
                l.getUuid(),
                l.getPerson().getUuid(),
                DisplayRef.of(body.getUuid(), body.getCode(), body.getName()),
                l.getLicenseNumber(),
                l.getVerificationStatus(),
                l.getVerifiedBy(),
                l.getVerifiedAt(),
                l.getCreatedAt(),
                l.getUpdatedAt());
    }
}
