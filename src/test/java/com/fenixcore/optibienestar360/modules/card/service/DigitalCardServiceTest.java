package com.fenixcore.optibienestar360.modules.card.service;

import com.fenixcore.optibienestar360.modules.card.dto.DigitalCardDto;
import com.fenixcore.optibienestar360.modules.card.entity.DigitalCardRow;
import com.fenixcore.optibienestar360.modules.card.repository.DigitalCardRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigitalCardService} — assembles the card DTO from the
 * V39 view row plus a generated QR, and 404s when the caller is not an enrolled
 * affiliate.
 */
@ExtendWith(MockitoExtension.class)
class DigitalCardServiceTest {

    @Mock private DigitalCardRepository repository;
    @Mock private QrCodeGenerator qrCodeGenerator;
    @Mock private BeneficiaryRepository beneficiaryRepository;

    private DigitalCardService sut() {
        return new DigitalCardService(repository, qrCodeGenerator, beneficiaryRepository);
    }

    @Test
    void getForUser_beneficiaryWithAccess_getsOwnIdentityOnHolderCoverage() {
        UUID userUuid = UUID.randomUUID();
        UUID memberUuid = UUID.randomUUID();
        Member member = new Member();
        member.setUuid(memberUuid);
        Person person = new Person();
        person.setFullName("Luis Pérez");
        person.setDocumentType("V");
        person.setDocumentNumber("30111222");
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setMember(member);
        beneficiary.setPerson(person);
        beneficiary.setRelationship(Beneficiary.Relationship.CHILD);
        UUID beneficiaryUuid = UUID.randomUUID();
        beneficiary.setUuid(beneficiaryUuid);
        DigitalCardRow holder = mock(DigitalCardRow.class);
        when(holder.getFullName()).thenReturn("Ana Pérez");
        when(holder.getPlanName()).thenReturn("Plan Familiar");
        when(repository.findByUserUuid(userUuid)).thenReturn(Optional.empty());
        when(beneficiaryRepository.findByUser_UuidAndActiveTrue(userUuid)).thenReturn(Optional.of(beneficiary));
        when(repository.findByMemberUuid(memberUuid)).thenReturn(Optional.of(holder));
        // The beneficiary's QR identifies them, not the holder.
        when(qrCodeGenerator.toPngDataUri(eq(beneficiaryUuid.toString()), anyInt())).thenReturn("data:image/png;base64,QR");

        DigitalCardDto dto = sut().getForUser(userUuid);

        assertThat(dto.qrCodeDataUri()).isEqualTo("data:image/png;base64,QR");
        assertThat(dto.fullName()).isEqualTo("Luis Pérez");
        assertThat(dto.documentNumber()).isEqualTo("30111222");
        assertThat(dto.planName()).isEqualTo("Plan Familiar");
        assertThat(dto.holderName()).isEqualTo("Ana Pérez");
        assertThat(dto.relationship()).isEqualTo("CHILD");
        assertThat(dto.memberUuid()).isEqualTo(memberUuid);
    }

    @Test
    void getForUser_buildsCardWithQr() {
        UUID userUuid = UUID.randomUUID();
        UUID memberUuid = UUID.randomUUID();
        DigitalCardRow row = mock(DigitalCardRow.class);
        when(row.getMemberUuid()).thenReturn(memberUuid);
        when(row.getFullName()).thenReturn("Ana Pérez");
        when(repository.findByUserUuid(userUuid)).thenReturn(Optional.of(row));
        when(qrCodeGenerator.toPngDataUri(eq(memberUuid.toString()), anyInt()))
                .thenReturn("data:image/png;base64,QR");

        DigitalCardDto dto = sut().getForUser(userUuid);

        assertThat(dto.memberUuid()).isEqualTo(memberUuid);
        assertThat(dto.fullName()).isEqualTo("Ana Pérez");
        assertThat(dto.qrCodeDataUri()).isEqualTo("data:image/png;base64,QR");
    }

    @Test
    void getForUser_404_whenNotEnrolled() {
        UUID userUuid = UUID.randomUUID();
        when(repository.findByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().getForUser(userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.member.not_enrolled");
    }
}
