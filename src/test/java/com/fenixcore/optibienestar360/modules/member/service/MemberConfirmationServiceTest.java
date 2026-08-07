package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MemberConfirmationService} — manual member
 * confirmation for members (typically subsidized) that never generate a
 * payment, so the automatic confirmation hook in
 * {@code PaymentsService.approve} never fires for them.
 */
@ExtendWith(MockitoExtension.class)
class MemberConfirmationServiceTest {

    @Mock private MemberRepository memberRepository;
    @InjectMocks private MemberConfirmationService service;

    @Test
    void confirm_stampsConfirmedAt() {
        Member member = new Member();
        member.setUuid(UUID.randomUUID());
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));

        Instant result = service.confirm(member.getUuid());

        assertThat(member.getConfirmedAt()).isEqualTo(result);
    }

    @Test
    void confirm_404_whenMemberUnknown() {
        UUID missing = UUID.randomUUID();
        when(memberRepository.findByUuid(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(missing))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("member.not_found");
    }

    @Test
    void confirm_422_whenAlreadyConfirmed() {
        Member member = new Member();
        member.setUuid(UUID.randomUUID());
        member.setConfirmedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.confirm(member.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.already_confirmed");
    }
}
