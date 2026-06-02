package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.core.util.ListQuery;
import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.MedicalSpecialty;
import com.fenixcore.optisaludplus.modules.catalog.repository.MedicalSpecialtyRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicalSpecialtyService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final MedicalSpecialtyRepository repository;

    @Autowired @Lazy
    private MedicalSpecialtyService self;

    public Page<MedicalSpecialtyDto> list(Pageable pageable, String filter, String q) {
        if (ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<MedicalSpecialty> spec = (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "medical_specialty.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(MedicalSpecialtyService::toDto);
    }

    @Cacheable(value = "catalogs", key = "'medical_specialty:all'")
    public List<MedicalSpecialtyDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(MedicalSpecialtyService::toDto)
                .toList();
    }

    public MedicalSpecialtyDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public MedicalSpecialtyDto create(MedicalSpecialtyCreateRequest req) {
        MedicalSpecialty m = new MedicalSpecialty();
        m.setCode(req.code());
        m.setName(req.name());
        m.setDescription(req.description());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public MedicalSpecialtyDto update(UUID uuid, MedicalSpecialtyUpdateRequest req) {
        MedicalSpecialty m = find(uuid);
        m.setName(req.name());
        m.setDescription(req.description());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
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
