package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.card.service.QrCodeGenerator;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * The promoter's unique affiliation QR ({@code GET /v1/promoter/me/qr}). The QR
 * encodes the public affiliation link carrying the promoter's referral code
 * ({@code promoters.referral_code}, unique), so every new affiliation that
 * arrives through it is attributed to that promoter.
 */
@RestController
@RequestMapping("/v1/promoter/me/qr")
@RequiredArgsConstructor
public class PromoterQrController {

    private static final int QR_SIZE_PX = 480;

    private final PromoterRepository promoterRepository;
    private final QrCodeGenerator qrCodeGenerator;

    @Value("${app.affiliation-url:http://localhost:3000/afiliarse}")
    private String affiliationUrl;

    public record PromoterQrDto(String displayName, String referralCode, String affiliationUrl, String qrCodeDataUri) {}

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_OWN')")
    @Transactional(readOnly = true)
    public ResponseEntity<PromoterQrDto> myQr(@AuthenticationPrincipal CustomUserDetails actor) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actor.getUuid())
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        String link = affiliationUrl + "?ref=" + URLEncoder.encode(promoter.getReferralCode(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(new PromoterQrDto(
                promoter.getDisplayName(), promoter.getReferralCode(), link,
                qrCodeGenerator.toPngDataUri(link, QR_SIZE_PX)));
    }
}
