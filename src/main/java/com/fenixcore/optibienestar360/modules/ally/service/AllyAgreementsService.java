package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Admin-side CRUD for the {@link AllyAgreement} entity scoped to a parent
 * {@link Ally}. Same pattern as {@link AllyServicesAdminService}: every
 * write resolves the parent ally + verifies the agreement actually belongs
 * to it before mutating.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyAgreementsService {

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(AllyAgreement.class, Map.of());

    private final AllyRepository allyRepository;
    private final AllyAgreementRepository agreementRepository;
    private final AllyMapper mapper;
    private final DefaultSortResolver defaultSortResolver;

    public List<AllyAgreementDto> listForAlly(UUID allyUuid, Pageable pageable) {
        Ally ally = findAlly(allyUuid);
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted("ally_agreement", pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, "ally_agreement");
        return agreementRepository.findByAllyIdAndActiveTrue(ally.getId(), resolved.getSort()).stream()
                .map(mapper::toAgreementDto)
                .toList();
    }

    public AllyAgreementDto get(UUID allyUuid, UUID agreementUuid) {
        return mapper.toAgreementDto(findAgreementUnderAlly(allyUuid, agreementUuid));
    }

    @Transactional
    @Auditable(entity = "ally_agreement", action = AuditAction.CREATE)
    public AllyAgreementDto create(UUID allyUuid, AllyAgreementCreateRequest req) {
        Ally ally = findAlly(allyUuid);
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);
        agreement.setAgreementType(req.agreementType());
        agreement.setStartDate(req.startDate());
        agreement.setEndDate(req.endDate());
        agreement.setTerms(req.terms());
        agreement.setSignedPdfUrl(req.signedPdfUrl());
        agreement.setStatus(req.status() != null ? req.status() : "ACTIVE");
        return mapper.toAgreementDto(agreementRepository.save(agreement));
    }

    @Transactional
    @Auditable(entity = "ally_agreement", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public AllyAgreementDto update(UUID allyUuid, UUID agreementUuid, AllyAgreementUpdateRequest req) {
        AllyAgreement agreement = findAgreementUnderAlly(allyUuid, agreementUuid);
        if (req.agreementType() != null) agreement.setAgreementType(req.agreementType());
        if (req.startDate() != null)     agreement.setStartDate(req.startDate());
        if (req.endDate() != null)       agreement.setEndDate(req.endDate());
        if (req.terms() != null)         agreement.setTerms(req.terms());
        if (req.signedPdfUrl() != null)  agreement.setSignedPdfUrl(req.signedPdfUrl());
        if (req.status() != null)        agreement.setStatus(req.status());
        if (req.active() != null)        agreement.setActive(req.active());
        return mapper.toAgreementDto(agreement);
    }

    @Transactional
    @Auditable(entity = "ally_agreement", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void delete(UUID allyUuid, UUID agreementUuid) {
        AllyAgreement agreement = findAgreementUnderAlly(allyUuid, agreementUuid);
        agreement.setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Ally findAlly(UUID uuid) {
        return allyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    private AllyAgreement findAgreementUnderAlly(UUID allyUuid, UUID agreementUuid) {
        AllyAgreement agreement = agreementRepository.findByUuid(agreementUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_agreement.not_found"));
        if (!agreement.getAlly().getUuid().equals(allyUuid)) {
            throw new NoSuchElementException("ally_agreement.not_found");
        }
        return agreement;
    }
}
