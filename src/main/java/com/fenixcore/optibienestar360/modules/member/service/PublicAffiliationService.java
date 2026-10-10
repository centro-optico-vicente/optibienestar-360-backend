package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.member.PublicAffiliationController.PublicAffiliationRequest;
import com.fenixcore.optibienestar360.modules.member.PublicAffiliationController.PublicAffiliationResponse;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipsService;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Public self-affiliation (promoter QR → /afiliarse). One transaction:
 * <ol>
 *   <li>AFILIADO user with the password the applicant chose;</li>
 *   <li>member on the same Person, attributed to the referral code's promoter;</li>
 *   <li>membership of the chosen plan, forced to SUSPENDED — the validator only
 *       reads the lifecycle status, so an ACTIVE membership here would grant
 *       benefits before paying. Approving the first payment re-evaluates it
 *       ({@code MembershipChargeService.applyPayment}) and staff can always
 *       reactivate it.</li>
 * </ol>
 * A cédula or email that already exists is rejected: attaching a new login to
 * an existing Person would hand a stranger that person's data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicAffiliationService {

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PromoterRepository promoterRepository;
    private final MembershipRepository membershipRepository;
    private final UserService userService;
    private final MembersService membersService;
    private final MembershipsService membershipsService;

    @Transactional
    public PublicAffiliationResponse create(PublicAffiliationRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (personRepository.existsByDocumentTypeAndDocumentNumber(req.documentType(), req.documentNumber())) {
            throw new IllegalArgumentException("affiliation.document.exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("user.email.exists");
        }

        String code = req.referralCode() == null || req.referralCode().isBlank()
                ? null : req.referralCode().trim().toUpperCase(Locale.ROOT);
        Promoter promoter = code == null ? null : promoterRepository.findByReferralCode(code)
                .orElseThrow(() -> new IllegalArgumentException("affiliation.referral.not_found"));

        Role afiliado = roleRepository.findByName("AFILIADO")
                .orElseThrow(() -> new IllegalStateException("role AFILIADO missing"));

        userService.createUser(new AdminCreateUserRequest(
                email, req.firstName().trim(), null, req.lastName().trim(), null, req.password(),
                req.documentType(), req.documentNumber(), null, null, blankToNull(req.phone()),
                List.of(afiliado.getUuid()), afiliado.getUuid()), null);

        MemberDetailDto member = membersService.create(new MemberCreateRequest(
                req.firstName().trim(), null, req.lastName().trim(), null,
                req.documentType(), req.documentNumber(), null, null,
                req.birthDate(), null, null, null, null, null,
                blankToNull(req.phone()), null, email, null, null, null, null, null, null, null,
                null, "Autoafiliación desde la página pública" + (code != null ? " · código " + code : ""),
                code));

        MembershipDto enrolled = membershipsService.enroll(member.uuid(), new MembershipCreateRequest(req.planUuid(), null, null, null, null), null);
        Membership membership = membershipRepository.findByUuid(enrolled.uuid())
                .orElseThrow(() -> new IllegalStateException("membership just created not found"));
        membership.setStatus(Membership.LifecycleStatus.SUSPENDED.name());

        log.info("Public affiliation created for member {} (referral {})", member.uuid(), code);
        return new PublicAffiliationResponse(member.uuid(), email, promoter != null ? promoter.getDisplayName() : null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
