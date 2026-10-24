package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.dto.SecurityPolicyDto;
import com.fenixcore.optibienestar360.modules.auth.dto.SecurityPolicyUpdateRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.SecurityPolicy;
import com.fenixcore.optibienestar360.modules.auth.repository.SecurityPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SecurityPolicyService} — read/update of the single
 * active {@code security_policies} row, falling back to the same in-code
 * defaults {@code AuthService} uses when no active row exists.
 */
@ExtendWith(MockitoExtension.class)
class SecurityPolicyServiceTest {

    @Mock private SecurityPolicyRepository repository;

    /** Built per-call so the {@code @Mock} field is already injected. */
    private SecurityPolicyService service() {
        return new SecurityPolicyService(repository);
    }

    private static SecurityPolicy policy(UUID uuid) {
        SecurityPolicy p = new SecurityPolicy();
        p.setUuid(uuid);
        p.setMaxLoginAttempts(5);
        p.setLockoutDurationMinutes(30);
        p.setDaysPasswordExpires(90);
        p.setPasswordHistoryCount(5);
        p.setMaxConcurrentSessions(3);
        return p;
    }

    @Test
    void getReturnsTheActiveRow() {
        UUID uuid = UUID.randomUUID();
        SecurityPolicy active = policy(uuid);
        active.setPasswordHistoryCount(2);
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(active));

        SecurityPolicyDto result = service().get();

        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(result.maxLoginAttempts()).isEqualTo(5);
        assertThat(result.lockoutDurationMinutes()).isEqualTo(30);
        assertThat(result.daysPasswordExpires()).isEqualTo(90);
        assertThat(result.passwordHistoryCount()).isEqualTo(2);
        assertThat(result.maxConcurrentSessions()).isEqualTo(3);
    }

    @Test
    void getFallsBackToEntityDefaultsWhenNoActiveRow() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());

        SecurityPolicyDto result = service().get();

        SecurityPolicy defaults = new SecurityPolicy();
        assertThat(result.uuid()).isNull();
        assertThat(result.maxLoginAttempts()).isEqualTo(defaults.getMaxLoginAttempts());
        assertThat(result.lockoutDurationMinutes()).isEqualTo(defaults.getLockoutDurationMinutes());
        assertThat(result.daysPasswordExpires()).isEqualTo(defaults.getDaysPasswordExpires());
        assertThat(result.passwordHistoryCount()).isEqualTo(defaults.getPasswordHistoryCount());
        assertThat(result.maxConcurrentSessions()).isEqualTo(defaults.getMaxConcurrentSessions());
    }

    @Test
    void updateAppliesEveryFieldToTheActiveRow() {
        UUID uuid = UUID.randomUUID();
        SecurityPolicy active = policy(uuid);
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(active));
        when(repository.save(any(SecurityPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityPolicyDto result = service().update(new SecurityPolicyUpdateRequest(10, 15, 60, 3, 2));

        assertThat(result.uuid()).isEqualTo(uuid);
        assertThat(active.getMaxLoginAttempts()).isEqualTo(10);
        assertThat(active.getLockoutDurationMinutes()).isEqualTo(15);
        assertThat(active.getDaysPasswordExpires()).isEqualTo(60);
        assertThat(active.getPasswordHistoryCount()).isEqualTo(3);
        assertThat(active.getMaxConcurrentSessions()).isEqualTo(2);
    }

    @Test
    void updateAcceptsZeroToDisableHistoryAndExpiry() {
        SecurityPolicy active = policy(UUID.randomUUID());
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(active));
        when(repository.save(any(SecurityPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityPolicyDto result = service().update(new SecurityPolicyUpdateRequest(5, 30, 0, 0, 3));

        assertThat(result.passwordHistoryCount()).isZero();
        assertThat(result.daysPasswordExpires()).isZero();
    }

    @Test
    void updateCreatesAnActiveRowWhenNoneExists() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());
        when(repository.save(any(SecurityPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        service().update(new SecurityPolicyUpdateRequest(4, 20, 30, 1, 1));

        ArgumentCaptor<SecurityPolicy> saved = ArgumentCaptor.forClass(SecurityPolicy.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getMaxLoginAttempts()).isEqualTo(4);
        assertThat(saved.getValue().getPasswordHistoryCount()).isEqualTo(1);
    }
}
