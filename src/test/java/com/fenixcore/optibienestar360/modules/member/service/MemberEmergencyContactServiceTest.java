package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.dto.EmergencyContactDto;
import com.fenixcore.optibienestar360.modules.member.dto.EmergencyContactUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord;
import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord.EmergencyContactRelationship;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MedicalRecordRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MemberEmergencyContactService}: it writes only the three
 * emergency-contact columns, creates the record when missing, never revives a
 * soft-deleted record, and scopes the advisor to their own portfolio.
 */
@ExtendWith(MockitoExtension.class)
class MemberEmergencyContactServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private PromoterRepository promoterRepository;

    private final UUID actorUuid = UUID.randomUUID();
    private final EmergencyContactUpdateRequest request =
            new EmergencyContactUpdateRequest(" María Pérez ", "04145550000", EmergencyContactRelationship.SPOUSE);

    private MemberEmergencyContactService sut() {
        return new MemberEmergencyContactService(memberRepository, medicalRecordRepository, promoterRepository);
    }

    @Test
    void update_withoutRecord_createsOneHoldingOnlyTheContact() {
        Member member = member(null);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId())).thenReturn(Optional.empty());
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        EmergencyContactDto dto = sut().update(member.getUuid(), request);

        ArgumentCaptor<MedicalRecord> saved = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(medicalRecordRepository).save(saved.capture());
        MedicalRecord record = saved.getValue();
        assertThat(record.getPerson()).isSameAs(member.getPerson());
        assertThat(record.getEmergencyContactName()).isEqualTo("María Pérez");
        assertThat(record.getEmergencyContactPhone()).isEqualTo("04145550000");
        assertThat(record.getEmergencyContactRelationship()).isEqualTo(EmergencyContactRelationship.SPOUSE);
        assertThat(record.getAllergies()).isNull();
        assertThat(record.getBloodType()).isNull();
        assertThat(dto.exists()).isTrue();
    }

    @Test
    void update_keepsClinicalDataAndSoftDeleteUntouched() {
        Member member = member(null);
        MedicalRecord existing = new MedicalRecord();
        existing.setPerson(member.getPerson());
        existing.setAllergies("Penicilina");
        existing.setActive(false);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId())).thenReturn(Optional.of(existing));
        when(medicalRecordRepository.save(existing)).thenReturn(existing);

        sut().update(member.getUuid(), request);

        assertThat(existing.getAllergies()).isEqualTo("Penicilina");
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getEmergencyContactPhone()).isEqualTo("04145550000");
    }

    @Test
    void update_blankValues_clearTheContact() {
        Member member = member(null);
        MedicalRecord existing = new MedicalRecord();
        existing.setPerson(member.getPerson());
        existing.setEmergencyContactName("Old");
        existing.setEmergencyContactPhone("0412");
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId())).thenReturn(Optional.of(existing));
        when(medicalRecordRepository.save(existing)).thenReturn(existing);

        EmergencyContactDto dto = sut().update(member.getUuid(), new EmergencyContactUpdateRequest(" ", null, null));

        assertThat(existing.getEmergencyContactName()).isNull();
        assertThat(existing.getEmergencyContactPhone()).isNull();
        assertThat(dto.exists()).isFalse();
    }

    @Test
    void get_withoutRecord_returnsEmptyState() {
        Member member = member(null);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId())).thenReturn(Optional.empty());

        assertThat(sut().get(member.getUuid())).isEqualTo(EmergencyContactDto.empty());
    }

    @Test
    void advisor_memberOfTheirPortfolio_canUpdate() {
        Promoter advisor = promoter(10L);
        Member member = member(advisor);
        when(promoterRepository.findActiveByUserUuid(actorUuid)).thenReturn(Optional.of(advisor));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId())).thenReturn(Optional.empty());
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(sut().updateForAdvisor(actorUuid, member.getUuid(), request).exists()).isTrue();
    }

    @Test
    void advisor_memberOfAnotherPortfolio_isNotFound() {
        Promoter advisor = promoter(10L);
        Member member = member(promoter(20L));
        when(promoterRepository.findActiveByUserUuid(actorUuid)).thenReturn(Optional.of(advisor));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> sut().updateForAdvisor(actorUuid, member.getUuid(), request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("promoter.member.not_in_portfolio");
        verify(medicalRecordRepository, never()).save(any());
    }

    @Test
    void advisor_memberWithoutAdvisor_isNotFound() {
        Promoter advisor = promoter(10L);
        Member member = member(null);
        when(promoterRepository.findActiveByUserUuid(actorUuid)).thenReturn(Optional.of(advisor));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> sut().getForAdvisor(actorUuid, member.getUuid()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("promoter.member.not_in_portfolio");
    }

    @Test
    void own_notEnrolled_isNotFound() {
        when(memberRepository.findByUserUuid(actorUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().updateOwn(actorUuid, request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.member.not_enrolled");
        verify(medicalRecordRepository, never()).save(any());
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static Member member(Promoter advisor) {
        Person person = new Person();
        person.setId(1L);
        Member m = new Member();
        m.setUuid(UUID.randomUUID());
        m.setPerson(person);
        m.setPromoter(advisor);
        return m;
    }

    private static Promoter promoter(Long id) {
        Promoter p = new Promoter();
        p.setId(id);
        return p;
    }
}
