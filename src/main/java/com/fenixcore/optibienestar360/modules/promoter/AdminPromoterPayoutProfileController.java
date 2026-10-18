package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutProfileDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutProfileUpsertRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterPayoutProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/promoters/{promoterUuid}/payout-profile} —
 * 1:1 advisor payout destination (V182, dossier section V).
 *
 * <ul>
 *   <li>{@code GET} — always 200 when the promoter exists; {@code exists}
 *       distinguishes a real row from "no profile yet". 404 only for an
 *       unknown promoter (hub ADR 0012).</li>
 *   <li>{@code PUT} — upsert (full replace), reactivates a soft-deleted row.</li>
 *   <li>{@code DELETE} — soft-delete; a later GET returns {@code exists=false}.</li>
 * </ul>
 *
 * <p>Auth reuses {@code PROMOTER_VIEW_ALL} / {@code PROMOTER_UPDATE}. The
 * {@code OutPaymentFormModal} reads this endpoint to prefill payouts.</p>
 */
@RestController
@RequestMapping("/v1/admin/promoters/{promoterUuid}/payout-profile")
@RequiredArgsConstructor
public class AdminPromoterPayoutProfileController {

    private final PromoterPayoutProfileService payoutProfileService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<PromoterPayoutProfileDto> get(@PathVariable UUID promoterUuid) {
        return ResponseEntity.ok(payoutProfileService.getForPromoter(promoterUuid));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PROMOTER_UPDATE')")
    public ResponseEntity<PromoterPayoutProfileDto> upsert(@PathVariable UUID promoterUuid,
                                                           @Valid @RequestBody PromoterPayoutProfileUpsertRequest request) {
        return ResponseEntity.ok(payoutProfileService.upsert(promoterUuid, request));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('PROMOTER_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID promoterUuid) {
        payoutProfileService.delete(promoterUuid);
        return ResponseEntity.noContent().build();
    }
}
