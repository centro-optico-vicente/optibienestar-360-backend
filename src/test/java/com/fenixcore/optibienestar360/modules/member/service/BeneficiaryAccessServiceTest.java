package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Only the holder manages the family's portal access: a beneficiary logged in
 * with their own account (no member record of their own) can't grant or
 * revoke anyone — the holder included, who has no beneficiary row at all —
 * and a holder can't touch another family's beneficiary.
 */
@ExtendWith(MockitoExtension.class)
class BeneficiaryAccessServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserService userService;
    @Mock private AuthService authService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MemberMapper mapper;

    @InjectMocks private BeneficiaryAccessService service;

    private static Member member(long id) {
        Member m = new Member();
        m.setId(id);
        m.setActive(true);
        return m;
    }

    private static Beneficiary beneficiaryOf(Member member) {
        Beneficiary b = new Beneficiary();
        b.setMember(member);
        b.setActive(true);
        User user = new User();
        user.setActive(true);
        b.setUser(user);
        return b;
    }

    @Test
    void beneficiaryAccount_cannotRevokeAnyone() {
        UUID beneficiaryUser = UUID.randomUUID();
        when(memberRepository.findByUserUuid(beneficiaryUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(beneficiaryUser, UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.member.not_enrolled");
    }

    @Test
    void beneficiaryAccount_cannotGrantAnyone() {
        UUID beneficiaryUser = UUID.randomUUID();
        when(memberRepository.findByUserUuid(beneficiaryUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(beneficiaryUser, UUID.randomUUID(), null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.member.not_enrolled");
    }

    @Test
    void holder_cannotRevokeAnotherFamilysBeneficiary() {
        UUID holderUser = UUID.randomUUID();
        UUID otherBeneficiary = UUID.randomUUID();
        when(memberRepository.findByUserUuid(holderUser)).thenReturn(Optional.of(member(1L)));
        Beneficiary foreign = beneficiaryOf(member(2L));
        when(beneficiaryRepository.findByUuid(otherBeneficiary)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.revoke(holderUser, otherBeneficiary))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("beneficiary.not_found");
        assertThat(foreign.getUser().isActive()).isTrue();
    }

    @Test
    void holder_revokesOwnBeneficiary_deactivatingTheirUser() {
        UUID holderUser = UUID.randomUUID();
        UUID own = UUID.randomUUID();
        Member holder = member(1L);
        when(memberRepository.findByUserUuid(holderUser)).thenReturn(Optional.of(holder));
        Beneficiary beneficiary = beneficiaryOf(holder);
        when(beneficiaryRepository.findByUuid(own)).thenReturn(Optional.of(beneficiary));

        service.revoke(holderUser, own);

        assertThat(beneficiary.getUser().isActive()).isFalse();
    }
}
