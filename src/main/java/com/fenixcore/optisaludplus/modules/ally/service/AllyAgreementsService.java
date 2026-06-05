package com.fenixcore.optisaludplus.modules.ally.service;

import com.fenixcore.optisaludplus.modules.ally.dto.AllyAgreementCreateRequest;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyAgreementUpdateRequest;
import com.fenixcore.optisaludplus.modules.ally.entity.Ally;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyAgreement;
import com.fenixcore.optisaludplus.modules.ally.mapper.AllyMapper;
import com.fenixcore.optisaludplus.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optisaludplus.modules.ally.repository.AllyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    private final AllyRepository allyRepository;
    private final AllyAgreementRepository agreementRepository;
    private final AllyMapper mapper;

    public List<AllyAgreementDto> listForAlly(UUID allyUuid) {
        Ally ally = findAlly(allyUuid);
        return agreementRepository.findByAllyIdAndActiveTrue(ally.getId()).stream()
                .map(mapper::toAgreementDto)
                .toList();
    }

    public AllyAgreementDto get(UUID allyUuid, UUID agreementUuid) {
        return mapper.toAgreementDto(findAgreementUnderAlly(allyUuid, agreementUuid));
    }

    @Transactional
    public AllyAgreementDto create(UUID allyUuid, AllyAgreementCreateRequest req) {
        Ally ally = findAlly(allyUuid);
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);
        agreement.setAgreementType(req.agreementType());
        agreement.setStartDate(req.startDate());
        agreement.setEndDate(req.endDate());
        agreement.setTerms(req.terms());
        agreement.setSignedPdfUrl(req.signedPdfUrl());
        if (req.status() != null) {
            agreement.setStatus(req.status());
        }
        return mapper.toAgreementDto(agreementRepository.save(agreement));
    }

    @Transactional
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
