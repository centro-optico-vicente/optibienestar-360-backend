package com.fenixcore.optibienestar360.modules.ally.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllyLicensesService} (V213 / hub ADR 0031 §5): licenses
 * hang from the ally's holder person, a number is unique per body, editing
 * resets the verification, and only VERIFIED licenses of a published ally
 * are public.
 */
@ExtendWith(MockitoExtension.class)
class AllyLicensesServiceTest {

    @Mock private AllyRepository allyRepository;
    @Mock private LicensingBodyRepository licensingBodyRepository;
    @Mock private PersonProfessionalLicenseRepository licenseRepository;

    private final Person holder = person(10L);
    private final LicensingBody mpps = body(1L, "MPPS");

    private AllyLicensesService sut() {
        return new AllyLicensesService(allyRepository, licensingBodyRepository, licenseRepository);
    }

    @Test
    void add_withoutHolderPerson_isRejected() {
        Ally ally = ally(null, true);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        assertThatThrownBy(() -> sut().add(ally.getUuid(), new AllyLicenseRequest(mpps.getUuid(), "12345")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("person_license.holder_required");
        verify(licenseRepository, never()).save(any());
    }

    @Test
    void add_duplicateNumberForTheBody_isRejected() {
        Ally ally = ally(holder, true);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(licensingBodyRepository.findByUuid(mpps.getUuid())).thenReturn(Optional.of(mpps));
        when(licenseRepository.existsByLicensingBody_IdAndLicenseNumberIgnoreCaseAndActiveTrue(1L, "12345"))
                .thenReturn(true);

        assertThatThrownBy(() -> sut().add(ally.getUuid(), new AllyLicenseRequest(mpps.getUuid(), " 12345 ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("person_license.number.duplicate");
    }

    @Test
    void add_isPendingOnTheHolderPerson() {
        Ally ally = ally(holder, true);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(licensingBodyRepository.findByUuid(mpps.getUuid())).thenReturn(Optional.of(mpps));
        when(licenseRepository.save(any(PersonProfessionalLicense.class))).thenAnswer(inv -> inv.getArgument(0));

        AllyLicenseDto dto = sut().add(ally.getUuid(), new AllyLicenseRequest(mpps.getUuid(), "12345"));

        assertThat(dto.personUuid()).isEqualTo(holder.getUuid());
        assertThat(dto.licenseNumber()).isEqualTo("12345");
        assertThat(dto.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void update_changingTheNumber_resetsTheVerification() {
        Ally ally = ally(holder, true);
        PersonProfessionalLicense license = license(holder, mpps, "111", VerificationStatus.VERIFIED);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(licenseRepository.findByUuid(license.getUuid())).thenReturn(Optional.of(license));
        when(licensingBodyRepository.findByUuid(mpps.getUuid())).thenReturn(Optional.of(mpps));

        AllyLicenseDto dto = sut().update(ally.getUuid(), license.getUuid(), new AllyLicenseRequest(mpps.getUuid(), "222"));

        assertThat(dto.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(license.getVerifiedAt()).isNull();
        assertThat(license.getVerifiedBy()).isNull();
    }

    @Test
    void licenseOfAnotherPerson_is404() {
        Ally ally = ally(holder, true);
        PersonProfessionalLicense foreign = license(person(99L), mpps, "111", VerificationStatus.PENDING);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(licenseRepository.findByUuid(foreign.getUuid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> sut().delete(ally.getUuid(), foreign.getUuid()))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(foreign.isActive()).isTrue();
    }

    @Test
    void verify_stampsWhoAndWhen() {
        Ally ally = ally(holder, true);
        PersonProfessionalLicense license = license(holder, mpps, "111", VerificationStatus.PENDING);
        UUID actor = UUID.randomUUID();
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(licenseRepository.findByUuid(license.getUuid())).thenReturn(Optional.of(license));

        AllyLicenseDto dto = sut().verify(ally.getUuid(), license.getUuid(),
                new AllyLicenseVerifyRequest(VerificationStatus.VERIFIED), actor);

        assertThat(dto.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(dto.verifiedBy()).isEqualTo(actor);
        assertThat(dto.verifiedAt()).isNotNull();
    }

    @Test
    void verify_backToPending_isRejected() {
        assertThatThrownBy(() -> sut().verify(UUID.randomUUID(), UUID.randomUUID(),
                new AllyLicenseVerifyRequest(VerificationStatus.PENDING), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("person_license.verification.invalid");
    }

    @Test
    void publicList_onlyVerifiedOfAPublishedAlly() {
        Ally ally = ally(holder, true);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(licenseRepository.findByPerson_IdAndActiveTrueAndVerificationStatus(eq(10L), eq(VerificationStatus.VERIFIED), any()))
                .thenReturn(List.of(license(holder, mpps, "12345", VerificationStatus.VERIFIED)));

        List<PublicAllyLicenseDto> rows = sut().publicList(ally.getUuid());

        assertThat(rows).containsExactly(new PublicAllyLicenseDto("MPPS", "Body MPPS", "12345"));
    }

    @Test
    void publicList_unpublishedAlly_is404() {
        Ally ally = ally(holder, false);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        assertThatThrownBy(() -> sut().publicList(ally.getUuid()))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static Ally ally(Person holder, boolean published) {
        Ally a = new Ally();
        a.setUuid(UUID.randomUUID());
        a.setHolderPerson(holder);
        a.setPublished(published);
        return a;
    }

    private static Person person(Long id) {
        Person p = new Person();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        return p;
    }

    private static LicensingBody body(Long id, String code) {
        LicensingBody b = new LicensingBody();
        b.setId(id);
        b.setUuid(UUID.randomUUID());
        b.setCode(code);
        b.setName("Body " + code);
        return b;
    }

    private static PersonProfessionalLicense license(Person person, LicensingBody body, String number,
                                                     VerificationStatus status) {
        PersonProfessionalLicense l = new PersonProfessionalLicense();
        l.setUuid(UUID.randomUUID());
        l.setPerson(person);
        l.setLicensingBody(body);
        l.setLicenseNumber(number);
        l.setVerificationStatus(status);
        if (status != VerificationStatus.PENDING) {
            l.setVerifiedAt(Instant.now());
            l.setVerifiedBy(UUID.randomUUID());
        }
        return l;
    }
}
