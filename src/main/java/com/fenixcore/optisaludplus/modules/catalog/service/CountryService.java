package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.CountryCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.CountryDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CountryUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.Country;
import com.fenixcore.optisaludplus.modules.catalog.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CountryService {

    private final CountryRepository repository;

    public List<CountryDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(CountryService::toDto).toList();
    }

    public CountryDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public CountryDto create(CountryCreateRequest req) {
        Country c = new Country();
        c.setIsoCode(req.isoCode());
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    public CountryDto update(UUID uuid, CountryUpdateRequest req) {
        Country c = find(uuid);
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    public void delete(UUID uuid) {
        Country c = find(uuid);
        c.setActive(false);
        repository.save(c);
    }

    private Country find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Country not found: " + uuid));
    }

    static CountryDto toDto(Country c) {
        return new CountryDto(c.getUuid(), c.getIsoCode(), c.getName(), c.isActive());
    }
}
