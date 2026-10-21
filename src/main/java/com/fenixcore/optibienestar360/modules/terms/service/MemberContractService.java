package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.MemberTermsPreviewRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The MEMBER contract rendered for an applicant who is not a member yet:
 * the live preview of the public affiliation form, and the text stored (and
 * re-rendered on approval) by the affiliation requests. Both go through
 * {@link #render}, so the form, the stored evidence and the approval-time
 * comparison always agree on the same text for the same data.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberContractService {

    private final TermsVersionService termsVersionService;
    private final TermsTemplateRenderer renderer;
    private final TermsVariableResolver variableResolver;
    private final PlanRepository planRepository;

    /**
     * The current public AFILIADO contract rendered with the form data.
     * Saves nothing. A plan the applicant can't pick on the public page
     * (inactive or unpublished) is left out, as if none was chosen.
     */
    public PublicTermsDto preview(TermType type, MemberTermsPreviewRequest req) {
        if (type != TermType.MEMBER) {
            throw new IllegalArgumentException("terms.preview.member_only");
        }
        TermsVersion version = termsVersionService.getCurrentPublic(TermType.MEMBER);
        Plan plan = req.planUuid() == null ? null : planRepository.findByUuid(req.planUuid())
                .filter(Plan::isActive)
                .filter(Plan::isPublished)
                .orElse(null);
        MemberTermsApplicant applicant = new MemberTermsApplicant(
                req.firstName(), req.lastName(), req.documentType(), req.documentNumber(), req.birthDate(), plan);
        return new PublicTermsDto(version.getUuid(), version.getTermType(), version.getTitle(),
                render(version, applicant), version.getValidFrom());
    }

    /** {@code version}'s template rendered with the organization and {@code applicant}'s values. */
    public String render(TermsVersion version, MemberTermsApplicant applicant) {
        return renderer.render(version.getContentMarkdown(), variableResolver.forApplicant(applicant));
    }

    /** Lower-case hex SHA-256 of {@code content} (UTF-8) — the fingerprint stored with an accepted text. */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
