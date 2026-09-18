package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCategoryCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCategoryDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCategoryUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentCategoryService;
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
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD over {@code payment_categories} (V115, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). Same 4-permission-
 * per-catalog convention as {@code AdminCurrencyController} —
 * {@code PAYMENT_CATEGORY_VIEW_ALL}/{@code _CREATE}/{@code _UPDATE}/
 * {@code _DELETE} (V119). Delete is soft-only — see
 * {@link PaymentCategoryService#delete}.
 */
@RestController
@RequestMapping("/v1/admin/payment-categories")
@RequiredArgsConstructor
public class AdminPaymentCategoryController {

    private static final String VIEW   = "hasAuthority('PAYMENT_CATEGORY_VIEW_ALL')";
    private static final String CREATE = "hasAuthority('PAYMENT_CATEGORY_CREATE')";
    private static final String UPDATE = "hasAuthority('PAYMENT_CATEGORY_UPDATE')";
    private static final String DELETE = "hasAuthority('PAYMENT_CATEGORY_DELETE')";

    private final PaymentCategoryService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<AppliedSortPage<PaymentCategoryDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<PaymentCategoryDto> page = service.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/options")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<OptionDto>> options(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(service.listOptions(q, limit, currentValues));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(VIEW)
    public ResponseEntity<PaymentCategoryDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize(CREATE)
    public ResponseEntity<PaymentCategoryDto> create(@Valid @RequestBody PaymentCategoryCreateRequest req) {
        PaymentCategoryDto created = service.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<PaymentCategoryDto> update(@PathVariable UUID uuid, @Valid @RequestBody PaymentCategoryUpdateRequest req) {
        return ResponseEntity.ok(service.update(uuid, req));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize(DELETE)
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
