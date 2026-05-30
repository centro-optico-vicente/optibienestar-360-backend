package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MaritalStatusUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.MaritalStatus;
import com.fenixcore.optisaludplus.modules.catalog.repository.MaritalStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaritalStatusService {

    private final MaritalStatusRepository repository;

    public List<MaritalStatusDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(MaritalStatusService::toDto).toList();
    }

    public MaritalStatusDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public MaritalStatusDto create(MaritalStatusCreateRequest req) {
        MaritalStatus m = new MaritalStatus();
        m.setCode(req.code());
        m.setName(req.name());
        return toDto(repository.save(m));
    }

    @Transactional
    public MaritalStatusDto update(UUID uuid, MaritalStatusUpdateRequest req) {
        MaritalStatus m = find(uuid);
        m.setName(req.name());
        return toDto(repository.save(m));
    }

    @Transactional
    public void delete(UUID uuid) {
        MaritalStatus m = find(uuid);
        m.setActive(false);
        repository.save(m);
    }

    private MaritalStatus find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("MaritalStatus not found: " + uuid));
    }

    static MaritalStatusDto toDto(MaritalStatus m) {
        return new MaritalStatusDto(m.getUuid(), m.getCode(), m.getName(), m.isActive());
    }
}
