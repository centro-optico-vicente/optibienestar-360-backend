package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.GenderCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.GenderDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.GenderUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.Gender;
import com.fenixcore.optisaludplus.modules.catalog.repository.GenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenderService {

    private final GenderRepository repository;

    public List<GenderDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(GenderService::toDto).toList();
    }

    public GenderDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public GenderDto create(GenderCreateRequest req) {
        Gender g = new Gender();
        g.setCode(req.code());
        g.setName(req.name());
        return toDto(repository.save(g));
    }

    @Transactional
    public GenderDto update(UUID uuid, GenderUpdateRequest req) {
        Gender g = find(uuid);
        g.setName(req.name());
        return toDto(repository.save(g));
    }

    @Transactional
    public void delete(UUID uuid) {
        Gender g = find(uuid);
        g.setActive(false);
        repository.save(g);
    }

    private Gender find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Gender not found: " + uuid));
    }

    static GenderDto toDto(Gender g) {
        return new GenderDto(g.getUuid(), g.getCode(), g.getName(), g.isActive());
    }
}
