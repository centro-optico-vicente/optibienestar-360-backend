package com.fenixcore.optibienestar360.modules.campaign;

import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignAudienceDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignAudienceRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignEffectivenessDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignExceptionDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignExceptionRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignRelaunchRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignTransactionLinkDto;
import com.fenixcore.optibienestar360.modules.campaign.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Admin CRUD + lifecycle over commission/incentive {@code Campaign}s (V120/
 * V124). Granular per V124: {@code CAMPAIGN_VIEW_ALL}/{@code _CREATE}/
 * {@code _UPDATE}/{@code _DELETE}, plus {@code CAMPAIGN_EXCEPTION_CREATE}/
 * {@code _DELETE} for the manual transaction-override sub-resource.
 * {@code /relaunch} is gated by create (it makes a new campaign row) and
 * {@code /effectiveness} by view (read-only report).
 */
@RestController
@RequestMapping("/v1/admin/campaigns")
@RequiredArgsConstructor
public class AdminCampaignController {

    private final CampaignService campaignService;

    @GetMapping
    @PreAuthorize("hasAuthority('CAMPAIGN_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<CampaignDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Page<CampaignDto> page = campaignService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, campaignService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CAMPAIGN_VIEW_ALL')")
    public ResponseEntity<CampaignDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(campaignService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    public ResponseEntity<CampaignDto> create(@Valid @RequestBody CampaignRequest request) {
        CampaignDto created = campaignService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CAMPAIGN_UPDATE')")
    public ResponseEntity<CampaignDto> update(@PathVariable UUID uuid, @Valid @RequestBody CampaignRequest request) {
        return ResponseEntity.ok(campaignService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CAMPAIGN_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        campaignService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /** Clones the campaign's config + anchored commission rules into a brand-new campaign with new dates. */
    @PostMapping("/{uuid}/relaunch")
    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    public ResponseEntity<CampaignDto> relaunch(@PathVariable UUID uuid,
                                                @Valid @RequestBody CampaignRelaunchRequest request) {
        CampaignDto created = campaignService.relaunch(uuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/v1/admin/campaigns/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{uuid}/effectiveness")
    @PreAuthorize("hasAuthority('CAMPAIGN_VIEW_ALL')")
    public ResponseEntity<CampaignEffectivenessDto> effectiveness(@PathVariable UUID uuid) {
        return ResponseEntity.ok(campaignService.effectiveness(uuid));
    }

    @PostMapping("/{uuid}/exceptions")
    @PreAuthorize("hasAuthority('CAMPAIGN_EXCEPTION_CREATE')")
    public ResponseEntity<CampaignDto> createException(@PathVariable UUID uuid,
                                                        @Valid @RequestBody CampaignExceptionRequest request) {
        return ResponseEntity.ok(campaignService.createException(uuid, request));
    }

    @DeleteMapping("/{uuid}/exceptions/{exceptionUuid}")
    @PreAuthorize("hasAuthority('CAMPAIGN_EXCEPTION_DELETE')")
    public ResponseEntity<Void> deleteException(@PathVariable UUID uuid, @PathVariable UUID exceptionUuid) {
        campaignService.deleteException(uuid, exceptionUuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uuid}/exceptions")
    @PreAuthorize("hasAuthority('CAMPAIGN_VIEW_ALL')")
    public ResponseEntity<Page<CampaignExceptionDto>> listExceptions(
            @PathVariable UUID uuid, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(campaignService.listExceptions(uuid, pageable));
    }

    /** Resolved {@code campaign_transaction_links} rows — what actually counted towards the campaign. */
    @GetMapping("/{uuid}/transactions")
    @PreAuthorize("hasAuthority('CAMPAIGN_VIEW_ALL')")
    public ResponseEntity<Page<CampaignTransactionLinkDto>> listTransactions(
            @PathVariable UUID uuid, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(campaignService.listTransactions(uuid, pageable));
    }

    /**
     * Audience (only meaningful when {@code scope IN (INCLUDE, EXCLUDE)}) —
     * gated by {@code CAMPAIGN_UPDATE} since membership is part of editing
     * the campaign, same as {@code promoterUuids} on {@link CampaignRequest}.
     */
    @GetMapping("/{uuid}/audience")
    @PreAuthorize("hasAuthority('CAMPAIGN_UPDATE')")
    public ResponseEntity<Page<CampaignAudienceDto>> listAudience(
            @PathVariable UUID uuid, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(campaignService.listAudience(uuid, pageable));
    }

    @PostMapping("/{uuid}/audience")
    @PreAuthorize("hasAuthority('CAMPAIGN_UPDATE')")
    public ResponseEntity<CampaignAudienceDto> addAudienceMember(
            @PathVariable UUID uuid, @Valid @RequestBody CampaignAudienceRequest request) {
        CampaignAudienceDto created = campaignService.addAudienceMember(uuid, request.promoterUuid());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/v1/admin/campaigns/{uuid}/audience/{promoterUuid}")
                .buildAndExpand(uuid, request.promoterUuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{uuid}/audience/{promoterUuid}")
    @PreAuthorize("hasAuthority('CAMPAIGN_UPDATE')")
    public ResponseEntity<Void> removeAudienceMember(@PathVariable UUID uuid, @PathVariable UUID promoterUuid) {
        campaignService.removeAudienceMember(uuid, promoterUuid);
        return ResponseEntity.noContent().build();
    }
}
