package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.StateCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.Country;
import com.fenixcore.optisaludplus.modules.catalog.entity.State;
import com.fenixcore.optisaludplus.modules.catalog.repository.CountryRepository;
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
public class StateService {

    private final StateRepository repository;
    private final CountryRepository countryRepository;

    public List<StateDto> list(String countryIsoCode) {
        List<State> states = (countryIsoCode == null || countryIsoCode.isBlank())
                ? repository.findAllByActiveTrueOrderByName()
                : repository.findByCountry_IsoCodeAndActiveTrueOrderByName(countryIsoCode);
        return states.stream().map(StateService::toDto).toList();
    }

    public StateDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public StateDto create(StateCreateRequest req) {
        Country country = countryRepository.findByUuid(req.countryUuid())
                .orElseThrow(() -> new NoSuchElementException("Country not found: " + req.countryUuid()));
        State s = new State();
        s.setCountry(country);
        s.setCode(req.code());
        s.setName(req.name());
        return toDto(repository.save(s));
    }

    @Transactional
    public StateDto update(UUID uuid, StateUpdateRequest req) {
        State s = find(uuid);
        s.setName(req.name());
        return toDto(repository.save(s));
    }

    @Transactional
    public void delete(UUID uuid) {
        State s = find(uuid);
        s.setActive(false);
        repository.save(s);
    }

    private State find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("State not found: " + uuid));
    }

    static StateDto toDto(State s) {
        Country c = s.getCountry();
        return new StateDto(s.getUuid(), s.getCode(), s.getName(),
                c.getUuid(), c.getIsoCode(), s.isActive());
    }
}
