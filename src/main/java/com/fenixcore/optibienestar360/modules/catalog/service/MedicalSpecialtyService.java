package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.MedicalSpecialty;
import com.fenixcore.optibienestar360.modules.catalog.repository.MedicalSpecialtyRepository;
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
    private final AllyRepository allyRepository;

    @Autowired @Lazy
    private MedicalSpecialtyService self;

    public Page<MedicalSpecialtyDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<MedicalSpecialty> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "medical_specialty.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(MedicalSpecialtyService::toDto);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<MedicalSpecialty> spec = ((Specification<MedicalSpecialty>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                MedicalSpecialty::getUuid, MedicalSpecialty::getCode, MedicalSpecialtyService::labelOf, MedicalSpecialty::isActive);
    }

    private static String labelOf(MedicalSpecialty m) {
        return m.getCode() + " — " + m.getName();
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
        if (req.active() != null) {
            m.setActive(req.active());
        }
        return toDto(repository.save(m));
    }

    public long countUsages(UUID uuid) {
        return allyRepository.countBySpecialties_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid, boolean physical) {
        MedicalSpecialty m = find(uuid);
        long usages = countUsages(uuid);
        // physical=true is only honored when truly unused — never trust the client
        // flag blindly, to avoid violating the FK or losing referenced data on a
        // race condition between the "usage" ping and this call.
        if (physical && usages == 0) {
            repository.delete(m);
            return;
        }
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
