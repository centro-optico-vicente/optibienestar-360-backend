package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.UserInvitationService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterWithUserCreateRequest;
import com.fenixcore.optibienestar360.security.RoleNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Creates an advisor's login and advisor profile in one transaction (hub ADR
 * 0037 §5), composing the existing user and advisor creation so every rule of
 * both still applies (email uniqueness, SYSTEM-role guard, referral-code
 * uniqueness, one advisor per user). Without a password the invitation email
 * goes out once everything is created.
 */
@Service
@RequiredArgsConstructor
public class AdvisorOnboardingService {

    private final UserService userService;
    private final RoleRepository roleRepository;
    private final PromotersService promotersService;
    private final UserInvitationService invitationService;

    @Transactional
    public PromoterDto createWithUser(PromoterWithUserCreateRequest req, UUID actorUuid) {
        Role advisorRole = roleRepository.findByName(RoleNames.ADVISOR)
                .orElseThrow(() -> new IllegalStateException("role " + RoleNames.ADVISOR + " missing"));
        UserDto user = userService.createUser(new AdminCreateUserRequest(
                req.email(), req.firstName(), null, req.lastName(), null, req.password(),
                req.documentType(), req.documentNumber(), null, null, req.phone(),
                List.of(advisorRole.getUuid()), advisorRole.getUuid()), actorUuid);

        String displayName = req.displayName() == null || req.displayName().isBlank()
                ? (req.firstName().trim() + " " + req.lastName().trim())
                : req.displayName().trim();
        PromoterDto promoter = promotersService.create(new PromoterCreateRequest(
                displayName, req.description(), req.referralCode(), user.uuid(), req.email(), req.phone(),
                req.promoterTypeUuid(), req.joinedAt(), req.assignedZone(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null));

        if (req.password() == null || req.password().isBlank()) {
            invitationService.invite(user.uuid());
        }
        return promoter;
    }
}
