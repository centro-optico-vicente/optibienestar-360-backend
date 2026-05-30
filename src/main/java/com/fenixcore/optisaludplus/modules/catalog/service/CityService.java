package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.CityCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import com.fenixcore.optisaludplus.modules.catalog.entity.State;
import com.fenixcore.optisaludplus.modules.catalog.repository.CityRepository;
import com.fenixcore.optisaludplus.modules.catalog.repository.StateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

    private final CityRepository repository;
    private final StateRepository stateRepository;

    public List<CityDto> list(UUID stateUuid, String stateCode) {
        List<City> cities;
        if (stateUuid != null) {
            cities = repository.findByState_UuidAndActiveTrueOrderByName(stateUuid);
        } else if (stateCode != null && !stateCode.isBlank()) {
            cities = repository.findByState_CodeAndActiveTrueOrderByName(stateCode);
        } else {
            cities = repository.findAllByActiveTrueOrderByName();
        }
        return cities.stream().map(CityService::toDto).toList();
    }

    public CityDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public CityDto create(CityCreateRequest req) {
        State state = stateRepository.findByUuid(req.stateUuid())
                .orElseThrow(() -> new NoSuchElementException("State not found: " + req.stateUuid()));
        City c = new City();
        c.setState(state);
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    public CityDto update(UUID uuid, CityUpdateRequest req) {
        City c = find(uuid);
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    public void delete(UUID uuid) {
        City c = find(uuid);
        c.setActive(false);
        repository.save(c);
    }

    private City find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("City not found: " + uuid));
    }

    static CityDto toDto(City c) {
        State s = c.getState();
        return new CityDto(c.getUuid(), c.getName(),
                s.getUuid(), s.getCode(), c.isActive());
    }
}
