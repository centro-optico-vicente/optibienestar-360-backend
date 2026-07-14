package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapperImpl;
import com.fenixcore.optibienestar360.modules.member.repository.MedicalRecordRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Focused on the {@code getForMember} contract change: always 200 with a
 * DTO when the member exists; the {@code exists} flag distinguishes real
 * rows from the empty state. 404 stays only for an unknown member.
 */
@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;

    private MedicalRecordService service;

    @BeforeEach
    void setup() {
        service = new MedicalRecordService(memberRepository, medicalRecordRepository, new MemberMapperImpl());
    }

    // ─── getForMember: real row present ─────────────────────────────────────

    @Test
    void get_returns_populated_dto_with_exists_true_when_record_row_present() {
        Member member = memberWithPerson(10L, "Ana Perez");
        MedicalRecord row = new MedicalRecord();
        row.setId(1L);
        row.setUuid(UUID.randomUUID());
        row.setPerson(member.getPerson());
        row.setBloodType("O+");
        row.setActive(true);
        row.setStatus("ACTIVE");

        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId()))
                .thenReturn(Optional.of(row));

        MedicalRecordDto dto = service.getForMember(member.getUuid());

        assertThat(dto.exists()).isTrue();
        assertThat(dto.uuid()).isEqualTo(row.getUuid());
        assertThat(dto.personUuid()).isEqualTo(member.getPerson().getUuid());
        assertThat(dto.bloodType()).isEqualTo("O+");
        assertThat(dto.active()).isTrue();
        assertThat(dto.status()).isEqualTo("ACTIVE");
    }

    // ─── getForMember: no row / soft-deleted ───────────────────────────────

    @Test
    void get_returns_empty_dto_with_exists_false_when_no_row_present() {
        Member member = memberWithPerson(10L, "Ana Perez");
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId()))
                .thenReturn(Optional.empty());

        MedicalRecordDto dto = service.getForMember(member.getUuid());

        assertThat(dto.exists()).isFalse();
        assertThat(dto.uuid()).isNull();
        // personUuid stays populated so the frontend can still correlate
        assertThat(dto.personUuid()).isEqualTo(member.getPerson().getUuid());
        // All clinical fields null
        assertThat(dto.bloodType()).isNull();
        assertThat(dto.allergies()).isNull();
        assertThat(dto.chronicConditions()).isNull();
        assertThat(dto.currentMedications()).isNull();
        assertThat(dto.emergencyContactName()).isNull();
        assertThat(dto.emergencyContactPhone()).isNull();
        assertThat(dto.emergencyContactRelationship()).isNull();
        assertThat(dto.notes()).isNull();
        // Sensible defaults for lifecycle
        assertThat(dto.active()).isTrue();
        assertThat(dto.status()).isNull();
        assertThat(dto.createdAt()).isNull();
        assertThat(dto.updatedAt()).isNull();
    }

    @Test
    void get_returns_empty_dto_when_row_is_soft_deleted() {
        Member member = memberWithPerson(10L, "Ana Perez");
        MedicalRecord softDeleted = new MedicalRecord();
        softDeleted.setId(1L);
        softDeleted.setUuid(UUID.randomUUID());
        softDeleted.setPerson(member.getPerson());
        softDeleted.setBloodType("A-");
        softDeleted.setActive(false);  // soft-deleted

        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(medicalRecordRepository.findByPersonId(member.getPerson().getId()))
                .thenReturn(Optional.of(softDeleted));

        MedicalRecordDto dto = service.getForMember(member.getUuid());

        // Soft-deleted looks identical to "no row yet" for the frontend
        assertThat(dto.exists()).isFalse();
        assertThat(dto.uuid()).isNull();
        assertThat(dto.bloodType()).isNull();
        assertThat(dto.personUuid()).isEqualTo(member.getPerson().getUuid());
    }

    // ─── getForMember: unknown member UUID still 404 ───────────────────────

    @Test
    void get_throws_404_when_member_uuid_unknown() {
        UUID unknown = UUID.randomUUID();
        when(memberRepository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForMember(unknown))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("member.not_found");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static Member memberWithPerson(long id, String fullName) {
        Member m = new Member();
        m.setId(id);
        m.setUuid(UUID.randomUUID());
        m.setActive(true);

        Person p = new Person();
        p.setId(id + 100);
        p.setUuid(UUID.randomUUID());
        try {
            var f = Person.class.getDeclaredField("fullName");
            f.setAccessible(true);
            f.set(p, fullName);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        m.setPerson(p);
        return m;
    }
}
