package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.ServiceCategory;
import com.fenixcore.optisaludplus.modules.catalog.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCategoryService {

    private final ServiceCategoryRepository repository;

    public List<ServiceCategoryDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(ServiceCategoryService::toDto).toList();
    }

    public ServiceCategoryDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public ServiceCategoryDto create(ServiceCategoryCreateRequest req) {
        ServiceCategory s = new ServiceCategory();
        s.setCode(req.code());
        s.setName(req.name());
        s.setDescription(req.description());
        return toDto(repository.save(s));
    }

    @Transactional
    public ServiceCategoryDto update(UUID uuid, ServiceCategoryUpdateRequest req) {
        ServiceCategory s = find(uuid);
        s.setName(req.name());
        s.setDescription(req.description());
        return toDto(repository.save(s));
    }

    @Transactional
    public void delete(UUID uuid) {
        ServiceCategory s = find(uuid);
        s.setActive(false);
        repository.save(s);
    }

    private ServiceCategory find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ServiceCategory not found: " + uuid));
    }

    static ServiceCategoryDto toDto(ServiceCategory s) {
        return new ServiceCategoryDto(s.getUuid(), s.getCode(), s.getName(), s.getDescription(), s.isActive());
    }
}
