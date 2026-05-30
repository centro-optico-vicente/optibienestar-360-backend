package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.AllyType;
import com.fenixcore.optisaludplus.modules.catalog.repository.AllyTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyTypeService {

    private final AllyTypeRepository repository;

    public List<AllyTypeDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(AllyTypeService::toDto).toList();
    }

    public AllyTypeDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public AllyTypeDto create(AllyTypeCreateRequest req) {
        AllyType a = new AllyType();
        a.setCode(req.code());
        a.setName(req.name());
        a.setDescription(req.description());
        return toDto(repository.save(a));
    }

    @Transactional
    public AllyTypeDto update(UUID uuid, AllyTypeUpdateRequest req) {
        AllyType a = find(uuid);
        a.setName(req.name());
        a.setDescription(req.description());
        return toDto(repository.save(a));
    }

    @Transactional
    public void delete(UUID uuid) {
        AllyType a = find(uuid);
        a.setActive(false);
        repository.save(a);
    }

    private AllyType find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("AllyType not found: " + uuid));
    }

    static AllyTypeDto toDto(AllyType a) {
        return new AllyTypeDto(a.getUuid(), a.getCode(), a.getName(), a.getDescription(), a.isActive());
    }
}
