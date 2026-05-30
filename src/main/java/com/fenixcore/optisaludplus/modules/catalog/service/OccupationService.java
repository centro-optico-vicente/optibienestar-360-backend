package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.OccupationUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.Occupation;
import com.fenixcore.optisaludplus.modules.catalog.repository.OccupationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OccupationService {

    private final OccupationRepository repository;

    public List<OccupationDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(OccupationService::toDto).toList();
    }

    public OccupationDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public OccupationDto create(OccupationCreateRequest req) {
        Occupation o = new Occupation();
        o.setName(req.name());
        o.setDescription(req.description());
        return toDto(repository.save(o));
    }

    @Transactional
    public OccupationDto update(UUID uuid, OccupationUpdateRequest req) {
        Occupation o = find(uuid);
        o.setName(req.name());
        o.setDescription(req.description());
        return toDto(repository.save(o));
    }

    @Transactional
    public void delete(UUID uuid) {
        Occupation o = find(uuid);
        o.setActive(false);
        repository.save(o);
    }

    private Occupation find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Occupation not found: " + uuid));
    }

    static OccupationDto toDto(Occupation o) {
        return new OccupationDto(o.getUuid(), o.getName(), o.getDescription(), o.isActive());
    }
}
