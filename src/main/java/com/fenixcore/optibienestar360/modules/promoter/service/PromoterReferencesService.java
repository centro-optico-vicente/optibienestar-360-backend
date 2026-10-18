package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterReferenceCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterReferenceDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterReferenceUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterReference;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterReferenceRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Admin CRUD for an advisor's personal references (V180, dossier section IV),
 * mounted at {@code /v1/admin/promoters/{promoterUuid}/references}. Same
 * shape as the member beneficiaries sub-resource: every read/write resolves
 * the parent from the path and verifies the child belongs to it (404 on URL
 * tampering), delete is a soft-delete.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterReferencesService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

    private final PromoterRepository promoterRepository;
    private final PromoterReferenceRepository referenceRepository;

    public List<PromoterReferenceDto> listForPromoter(UUID promoterUuid) {
        Promoter promoter = findPromoter(promoterUuid);
        return referenceRepository.findByPromoterIdAndActiveTrue(promoter.getId(), DEFAULT_SORT).stream()
                .map(PromoterReferencesService::toDto)
                .toList();
    }

    public PromoterReferenceDto get(UUID promoterUuid, UUID referenceUuid) {
        return toDto(findUnderPromoter(promoterUuid, referenceUuid));
    }

    @Transactional
    @Auditable(entity = "promoter_reference", action = AuditAction.CREATE)
    public PromoterReferenceDto add(UUID promoterUuid, PromoterReferenceCreateRequest req) {
        Promoter promoter = findPromoter(promoterUuid);
        ensureNotSystemRow(promoter);

        PromoterReference reference = new PromoterReference();
        reference.setPromoter(promoter);
        reference.setFirstName(req.firstName().trim());
        reference.setLastName(req.lastName().trim());
        reference.setDocumentType(blankToNull(req.documentType()));
        reference.setDocumentNumber(blankToNull(req.documentNumber()));
        reference.setPhone(blankToNull(req.phone()));
        ensureDocumentPair(reference);

        return toDto(referenceRepository.save(reference));
    }

    @Transactional
    @Auditable(entity = "promoter_reference", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public PromoterReferenceDto update(UUID promoterUuid, UUID referenceUuid, PromoterReferenceUpdateRequest req) {
        PromoterReference reference = findUnderPromoter(promoterUuid, referenceUuid);
        ensureNotSystemRow(reference.getPromoter());

        if (req.firstName() != null && !req.firstName().isBlank()) reference.setFirstName(req.firstName().trim());
        if (req.lastName() != null && !req.lastName().isBlank())   reference.setLastName(req.lastName().trim());
        if (req.documentType() != null)   reference.setDocumentType(blankToNull(req.documentType()));
        if (req.documentNumber() != null) reference.setDocumentNumber(blankToNull(req.documentNumber()));
        if (req.phone() != null)          reference.setPhone(blankToNull(req.phone()));
        ensureDocumentPair(reference);

        return toDto(reference);  // managed → dirty-check on commit
    }

    @Transactional
    @Auditable(entity = "promoter_reference", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void delete(UUID promoterUuid, UUID referenceUuid) {
        PromoterReference reference = findUnderPromoter(promoterUuid, referenceUuid);
        reference.setActive(false);
        reference.setStatus("INACTIVE");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Promoter findPromoter(UUID uuid) {
        return promoterRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
    }

    /** Soft-deleted references are treated as gone (404), same as an unknown uuid. */
    private PromoterReference findUnderPromoter(UUID promoterUuid, UUID referenceUuid) {
        PromoterReference reference = referenceRepository.findByUuid(referenceUuid)
                .filter(PromoterReference::isActive)
                .orElseThrow(() -> new NoSuchElementException("promoter_reference.not_found"));
        if (!reference.getPromoter().getUuid().equals(promoterUuid)) {
            throw new NoSuchElementException("promoter_reference.not_found");
        }
        return reference;
    }

    private static void ensureNotSystemRow(Promoter promoter) {
        if (promoter.isSystem()) {
            throw new IllegalArgumentException("promoter.system.not_editable");
        }
    }

    /** Mirrors the V180 CHECK (both or neither) for a clean 422 instead of a constraint violation. */
    private static void ensureDocumentPair(PromoterReference reference) {
        if ((reference.getDocumentType() == null) != (reference.getDocumentNumber() == null)) {
            throw new IllegalArgumentException("promoter_reference.document.incomplete");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static PromoterReferenceDto toDto(PromoterReference r) {
        return new PromoterReferenceDto(
                r.getUuid(),
                r.getPromoter().getUuid(),
                r.getFirstName(),
                r.getLastName(),
                r.getDocumentType(),
                r.getDocumentNumber(),
                r.getPhone(),
                r.isActive(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
