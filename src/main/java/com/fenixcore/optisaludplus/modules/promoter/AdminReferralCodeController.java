package com.fenixcore.optisaludplus.modules.promoter;

import com.fenixcore.optisaludplus.modules.promoter.dto.ReferralCodeIssueRequest;
import com.fenixcore.optisaludplus.modules.promoter.dto.ReferralCodeIssueResponse;
import com.fenixcore.optisaludplus.modules.promoter.service.ReferralCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint to issue / regenerate the affiliate referral code on a
 * Member. The code lives on {@code members.referral_code} (V27); this
 * is the canonical write path. Promoter referral codes are managed via
 * {@code POST /v1/admin/promoters}, not here.
 */
@RestController
@RequestMapping("/v1/admin/referral-codes")
@RequiredArgsConstructor
public class AdminReferralCodeController {

    private final ReferralCodeService referralCodeService;

    @PostMapping
    @PreAuthorize("hasAuthority('REFERRAL_CODE_CREATE')")
    public ResponseEntity<ReferralCodeIssueResponse> issue(
            @Valid @RequestBody ReferralCodeIssueRequest request) {
        return ResponseEntity.ok(referralCodeService.issue(request));
    }
}
