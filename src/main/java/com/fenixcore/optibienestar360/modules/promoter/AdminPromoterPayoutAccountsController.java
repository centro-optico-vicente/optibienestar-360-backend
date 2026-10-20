package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutAccountDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutAccountRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterPayoutAccountsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/advisors/{promoterUuid}/payout-accounts} —
 * advisor payout accounts, N per advisor with one default (V184, dossier
 * section V). The {@code OutPaymentFormModal} reads the list to prefill
 * payouts.
 *
 * <p>Dedicated granular permissions ({@code PROMOTER_PAYOUT_ACCOUNT_*}):
 * payout destinations are sensitive financial data, so they are not
 * reachable with the generic {@code PROMOTER_*} set. "Set as default" is an
 * edit of the account, so it rides on {@code _UPDATE}.</p>
 */
@RestController
// Deprecated alias, remove once frontend >= #235 is deployed
@RequestMapping({"/v1/admin/advisors/{promoterUuid}/payout-accounts", "/v1/admin/promoters/{promoterUuid}/payout-accounts"})
@RequiredArgsConstructor
public class AdminPromoterPayoutAccountsController {

    private final PromoterPayoutAccountsService accountsService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL')")
    public ResponseEntity<List<PromoterPayoutAccountDto>> list(@PathVariable UUID promoterUuid) {
        return ResponseEntity.ok(accountsService.listForPromoter(promoterUuid));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL')")
    public ResponseEntity<PromoterPayoutAccountDto> get(@PathVariable UUID promoterUuid, @PathVariable UUID uuid) {
        return ResponseEntity.ok(accountsService.get(promoterUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROMOTER_PAYOUT_ACCOUNT_CREATE')")
    public ResponseEntity<PromoterPayoutAccountDto> create(@PathVariable UUID promoterUuid,
                                                           @Valid @RequestBody PromoterPayoutAccountRequest request) {
        PromoterPayoutAccountDto created = accountsService.create(promoterUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_PAYOUT_ACCOUNT_UPDATE')")
    public ResponseEntity<PromoterPayoutAccountDto> update(@PathVariable UUID promoterUuid,
                                                           @PathVariable UUID uuid,
                                                           @Valid @RequestBody PromoterPayoutAccountRequest request) {
        return ResponseEntity.ok(accountsService.update(promoterUuid, uuid, request));
    }

    @PutMapping("/{uuid}/default")
    @PreAuthorize("hasAuthority('PROMOTER_PAYOUT_ACCOUNT_UPDATE')")
    public ResponseEntity<PromoterPayoutAccountDto> setDefault(@PathVariable UUID promoterUuid, @PathVariable UUID uuid) {
        return ResponseEntity.ok(accountsService.setDefault(promoterUuid, uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_PAYOUT_ACCOUNT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID promoterUuid, @PathVariable UUID uuid) {
        accountsService.delete(promoterUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
