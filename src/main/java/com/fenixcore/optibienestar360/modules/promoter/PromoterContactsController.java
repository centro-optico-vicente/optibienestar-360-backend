package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionScoreDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PaymentPromiseRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberContactDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.ReminderRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterCollectionService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Delegated collection management self-service (v2 PDF 2.b) under
 * {@code /v1/promoter/me}. The promoter is resolved from the JWT; the service
 * verifies the target member belongs to the caller's portfolio.
 *
 * <p>Writes (reminder / payment-promise) require {@code PROMOTER_CONTACT_OWN}
 * (V36); reads (history / collection-score) reuse {@code PROMOTER_VIEW_OWN}
 * (V35). Both are granted to PROMOTOR + ADMINISTRADOR + SYSTEM.</p>
 */
@RestController
@RequestMapping("/v1/promoter/me")
@RequiredArgsConstructor
public class PromoterContactsController {

    private final PromoterCollectionService collectionService;

    @PostMapping("/contacts/{memberUuid}/reminder")
    @PreAuthorize("hasAuthority('PROMOTER_CONTACT_OWN')")
    public ResponseEntity<PromoterMemberContactDto> reminder(
            @PathVariable UUID memberUuid,
            @Valid @RequestBody(required = false) ReminderRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        String note = request == null ? null : request.note();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collectionService.registerReminder(actor.getUuid(), memberUuid, note));
    }

    @PostMapping("/contacts/{memberUuid}/payment-promise")
    @PreAuthorize("hasAuthority('PROMOTER_CONTACT_OWN')")
    public ResponseEntity<PromoterMemberContactDto> paymentPromise(
            @PathVariable UUID memberUuid,
            @Valid @RequestBody PaymentPromiseRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                collectionService.registerPaymentPromise(actor.getUuid(), memberUuid,
                        request.promisedAmount(), request.promisedAtDate(), request.note()));
    }

    @GetMapping("/contacts")
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_OWN')")
    public ResponseEntity<List<PromoterMemberContactDto>> contacts(
            @RequestParam UUID memberUuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(collectionService.listContacts(actor.getUuid(), memberUuid));
    }

    @GetMapping("/collection-score")
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_OWN')")
    public ResponseEntity<CollectionScoreDto> collectionScore(
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(collectionService.collectionScore(actor.getUuid()));
    }
}
