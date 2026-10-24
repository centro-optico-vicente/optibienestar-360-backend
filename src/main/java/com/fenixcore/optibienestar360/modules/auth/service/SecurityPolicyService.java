package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.auth.dto.SecurityPolicyDto;
import com.fenixcore.optibienestar360.modules.auth.dto.SecurityPolicyUpdateRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.SecurityPolicy;
import com.fenixcore.optibienestar360.modules.auth.repository.SecurityPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin read/update of the single active {@code security_policies} row
 * (seeded by V6). Same singleton shape as {@code OrganizationService}: no
 * list/create/delete, only view and replace the active values.
 *
 * <p>When no active row exists, {@code AuthService} falls back to
 * {@code new SecurityPolicy()} defaults; {@link #get()} returns those same
 * defaults so the admin UI shows what is actually enforced, and
 * {@link #update} persists them as a new active row.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityPolicyService {

    private final SecurityPolicyRepository repository;

    public SecurityPolicyDto get() {
        return toDto(repository.findFirstByActiveTrue().orElseGet(SecurityPolicy::new));
    }

    @Transactional
    @Auditable(entity = "security_policy", action = AuditAction.UPDATE)
    public SecurityPolicyDto update(SecurityPolicyUpdateRequest req) {
        SecurityPolicy policy = repository.findFirstByActiveTrue().orElseGet(SecurityPolicy::new);
        policy.setMaxLoginAttempts(req.maxLoginAttempts());
        policy.setLockoutDurationMinutes(req.lockoutDurationMinutes());
        policy.setDaysPasswordExpires(req.daysPasswordExpires());
        policy.setPasswordHistoryCount(req.passwordHistoryCount());
        policy.setMaxConcurrentSessions(req.maxConcurrentSessions());
        return toDto(repository.save(policy));
    }

    private static SecurityPolicyDto toDto(SecurityPolicy p) {
        return new SecurityPolicyDto(
                p.getUuid(),
                p.getMaxLoginAttempts(),
                p.getLockoutDurationMinutes(),
                p.getDaysPasswordExpires(),
                p.getPasswordHistoryCount(),
                p.getMaxConcurrentSessions());
    }
}
