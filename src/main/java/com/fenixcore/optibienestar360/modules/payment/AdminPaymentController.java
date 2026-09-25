package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.modules.payment.dto.PaymentApproveRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDiscountRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentLinesUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentRejectRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentSupportUrlDto;
import com.fenixcore.optibienestar360.modules.payment.dto.OutPaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.OutPaymentUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentsService;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Admin surface for the payments workflow. This commit lands only the
 * registration entry point ({@code POST}) and the single-record read
 * needed to inspect the result; approve / reject / list endpoints land in
 * follow-up bullets.
 *
 * <p>The registration endpoint accepts {@code multipart/form-data} with
 * two parts: a JSON {@code payment} part (the structured metadata) and an
 * optional {@code support} part (the proof-of-payment file). The file
 * part is metadata-only when {@code storage.r2.enabled=false} — the bytes
 * are discarded but the metadata lands so the admin sees that a proof was
 * attached. Once R2 is wired up in a follow-up bullet, the same code path
 * starts persisting the file content without further changes.</p>
 */
@RestController
@RequestMapping("/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentsService paymentsService;

    /**
     * List payments with the admin queue's canonical default — newest
     * {@code received_at} first. RSQL filter on the column allow-list
     * (see {@code PaymentsService.ALLOWED_FILTER_FIELDS}); free-text
     * {@code ?q} hits {@code referenceNumber}, {@code adminNotes},
     * {@code supportFileName}. The composite V23 index
     * {@code (status, received_at DESC) WHERE is_active} covers the
     * canonical "PENDING + newest first" use case.
     *
     * <p>{@code ?direction=} is separate from {@code ?filter=} — {@code IN}/
     * {@code OUT} constrains to one direction, {@code ALL} shows both (the
     * "Movimientos" screen), omitted defaults to {@code IN} (this screen's
     * historical "Pagos"/"Cobros generales" behavior). See {@code
     * PaymentsService.list} Javadoc for why this isn't just another RSQL
     * filter clause.</p>
     */
    @GetMapping
    @PreAuthorize("hasAuthority('COLLECTION_VIEW_ALL') or hasAuthority('PAYMENT_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<PaymentDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String direction) {
        Page<PaymentDto> page = paymentsService.list(pageable, filter, q, direction);
        return ResponseEntity.ok(new AppliedSortPage<>(page, paymentsService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_VIEW_ALL') or hasAuthority('PAYMENT_VIEW_ALL')")
    public ResponseEntity<PaymentDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(paymentsService.get(uuid));
    }

    /**
     * {@code ?draft=true} starts the payment at {@code DRAFT} instead of the
     * historical one-step {@code PENDING} (V117 lines feature design fork —
     * see hub plan) — lines then stay editable via {@code PUT .../lines}
     * until an explicit {@code PUT .../submit}. Omitted/{@code false} keeps
     * today's behavior unchanged.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('COLLECTION_CREATE')")
    public ResponseEntity<PaymentDto> register(
            @Valid @RequestPart("payment") PaymentCreateRequest request,
            @RequestPart(value = "support", required = false) MultipartFile supportFile,
            @RequestParam(required = false, defaultValue = "false") boolean draft) {
        PaymentDto created = paymentsService.register(request, supportFile, draft);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Replaces the entire lines collection of a {@code DRAFT} collection
     * payment (V117 lines feature) — rejected once the payment has left
     * {@code DRAFT}. Same permission tier as {@code register}: composing a
     * draft's lines is part of the same "not yet submitted" phase.
     */
    @PutMapping("/{uuid}/lines")
    @PreAuthorize("hasAuthority('COLLECTION_CREATE')")
    public ResponseEntity<PaymentDto> updateLines(
            @PathVariable UUID uuid,
            @Valid @RequestBody PaymentLinesUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.updateLines(uuid, request, actor.getUuid()));
    }

    /** {@code DRAFT → PENDING} — moves a draft collection into the admin review queue. */
    @PutMapping("/{uuid}/submit")
    @PreAuthorize("hasAuthority('COLLECTION_CREATE')")
    public ResponseEntity<PaymentDto> submit(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.submit(uuid, actor.getUuid()));
    }

    /**
     * {@code PENDING → DRAFT} — the only way to make a submitted payment's
     * lines editable again. Same permission tier as {@code delete}: both
     * "undo" a not-yet-reviewed submission.
     */
    @PutMapping("/{uuid}/reactivate")
    @PreAuthorize("hasAuthority('COLLECTION_DELETE')")
    public ResponseEntity<PaymentDto> reactivate(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.reactivateToDraft(uuid, actor.getUuid()));
    }

	@PostMapping(value = "/out", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('PAYMENT_CREATE')")
	public ResponseEntity<PaymentDto> registerOut(
			@Valid @RequestPart("payment") OutPaymentCreateRequest request,
			@RequestPart(value = "support", required = false) MultipartFile supportFile) {
		PaymentDto created = paymentsService.registerOut(request, supportFile);
		return ResponseEntity.status(201).body(created);
	}

	@PutMapping("/{uuid}/out")
	@PreAuthorize("hasAuthority('PAYMENT_UPDATE')")
	public ResponseEntity<PaymentDto> updateOut(@PathVariable UUID uuid, @Valid @RequestBody OutPaymentUpdateRequest request) {
		return ResponseEntity.ok(paymentsService.updateOut(uuid, request));
	}

	@DeleteMapping("/{uuid}/out")
	@PreAuthorize("hasAuthority('PAYMENT_DELETE')")
	public ResponseEntity<Void> deleteOut(@PathVariable UUID uuid) {
		paymentsService.removeOut(uuid);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{uuid}/out/process")
	@PreAuthorize("hasAuthority('PAYMENT_PROCESS')")
	public ResponseEntity<PaymentDto> processOut(@PathVariable UUID uuid) {
		return ResponseEntity.ok(paymentsService.processOut(uuid));
	}

	@PutMapping("/{uuid}/out/approve")
    @PreAuthorize("hasAuthority('PAYMENT_APPROVE')")
	public ResponseEntity<PaymentDto> approveOut(@PathVariable UUID uuid,
			@Valid @RequestBody(required = false) PaymentApproveRequest request,
			@AuthenticationPrincipal CustomUserDetails actor) {
		return ResponseEntity.ok(paymentsService.approveOut(uuid, request, actor.getUuid()));
	}

	@PutMapping("/{uuid}/out/reject")
	@PreAuthorize("hasAuthority('PAYMENT_REJECT')")
	public ResponseEntity<PaymentDto> rejectOut(@PathVariable UUID uuid,
			@Valid @RequestBody PaymentRejectRequest request,
			@AuthenticationPrincipal CustomUserDetails actor) {
		return ResponseEntity.ok(paymentsService.rejectOut(uuid, request, actor.getUuid()));
	}

	@PutMapping("/{uuid}/approve")
	@PreAuthorize("hasAuthority('COLLECTION_APPROVE')")
    public ResponseEntity<PaymentDto> approve(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) PaymentApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.approve(uuid, request, actor.getUuid()));
    }

    @PutMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('COLLECTION_REJECT')")
    public ResponseEntity<PaymentDto> reject(
            @PathVariable UUID uuid,
            @Valid @RequestBody PaymentRejectRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.reject(uuid, request, actor.getUuid()));
    }

    /**
     * Deletes a still-PENDING payment registered by mistake. The backend
     * rejects (422) deleting a payment that is already APPROVED/REJECTED —
     * those are the audited review outcomes.
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        paymentsService.remove(uuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Applies a one-off discount to a PENDING payment (v2 PDF item #1). Distinct
     * from a recurring subsidy — this condones/reduces this single charge.
     * Gated by {@code ALLOWS_DISCOUNT}; a {@code reason} is mandatory.
     */
    @PostMapping("/{uuid}/discount")
    @PreAuthorize("hasAuthority('ALLOWS_DISCOUNT')")
    public ResponseEntity<PaymentDto> applyDiscount(
            @PathVariable UUID uuid,
            @Valid @RequestBody PaymentDiscountRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(paymentsService.applyDiscount(uuid, request, actor.getUuid()));
    }

    /**
     * Returns a short-lived presigned download URL for the proof of
     * payment object in R2. The URL itself is opaque to the backend
     * — the frontend opens it in a new tab or fetches the file directly.
     *
     * <p>The {@code ttlMinutes} query param lets the caller request a
     * shorter or longer link; the service clamps it to [1, 60] minutes.
     * Defaults to 5 minutes when omitted.</p>
     */
    @GetMapping("/{uuid}/support")
	@PreAuthorize("hasAuthority('COLLECTION_VIEW_ALL') or hasAuthority('PAYMENT_VIEW_ALL')")
    public ResponseEntity<PaymentSupportUrlDto> getSupportUrl(
            @PathVariable UUID uuid,
            @RequestParam(value = "ttlMinutes", required = false)
            @Min(1) @Max(60) Integer ttlMinutes) {
        Duration ttl = ttlMinutes != null ? Duration.ofMinutes(ttlMinutes) : null;
        return ResponseEntity.ok(paymentsService.generateSupportUrl(uuid, ttl));
    }
}
