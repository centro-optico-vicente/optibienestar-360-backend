package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.MedicalSpecialty;
import com.fenixcore.optisaludplus.modules.catalog.repository.MedicalSpecialtyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicalSpecialtyService {

    private final MedicalSpecialtyRepository repository;

    public List<MedicalSpecialtyDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(MedicalSpecialtyService::toDto).toList();
    }

    public MedicalSpecialtyDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public MedicalSpecialtyDto create(MedicalSpecialtyCreateRequest req) {
        MedicalSpecialty m = new MedicalSpecialty();
        m.setCode(req.code());
        m.setName(req.name());
        m.setDescription(req.description());
        return toDto(repository.save(m));
    }

    @Transactional
    public MedicalSpecialtyDto update(UUID uuid, MedicalSpecialtyUpdateRequest req) {
        MedicalSpecialty m = find(uuid);
        m.setName(req.name());
        m.setDescription(req.description());
        return toDto(repository.save(m));
    }

    @Transactional
    public void delete(UUID uuid) {
        MedicalSpecialty m = find(uuid);
        m.setActive(false);
        repository.save(m);
    }

    private MedicalSpecialty find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("MedicalSpecialty not found: " + uuid));
    }

    static MedicalSpecialtyDto toDto(MedicalSpecialty m) {
        return new MedicalSpecialtyDto(m.getUuid(), m.getCode(), m.getName(), m.getDescription(), m.isActive());
    }
}
