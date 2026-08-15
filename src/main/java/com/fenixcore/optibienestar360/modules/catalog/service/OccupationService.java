package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.dto.OccupationCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.OccupationDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.OccupationUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.Occupation;
import com.fenixcore.optibienestar360.modules.catalog.repository.OccupationRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
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
public class OccupationService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("name", "description");
    private static final String[] SEARCHABLE_FIELDS = {"name", "description"};

    private final OccupationRepository repository;
    private final MemberRepository memberRepository;

    @Autowired @Lazy
    private OccupationService self;

    public Page<OccupationDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<Occupation> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "occupation.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(OccupationService::toDto);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. {@code code} is always null (Occupation has no own code). */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Occupation> spec = ((Specification<Occupation>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Occupation::getUuid, occupation -> null, Occupation::getName, Occupation::isActive);
    }

    @Cacheable(value = "catalogs", key = "'occupation:all'")
    public List<OccupationDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(OccupationService::toDto)
                .toList();
    }

    public OccupationDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public OccupationDto create(OccupationCreateRequest req) {
        Occupation o = new Occupation();
        o.setName(req.name());
        o.setDescription(req.description());
        return toDto(repository.save(o));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public OccupationDto update(UUID uuid, OccupationUpdateRequest req) {
        Occupation o = find(uuid);
        o.setName(req.name());
        o.setDescription(req.description());
        if (req.active() != null) {
            o.setActive(req.active());
        }
        return toDto(repository.save(o));
    }

    public long countUsages(UUID uuid) {
        return memberRepository.countByOccupation_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid, boolean physical) {
        Occupation o = find(uuid);
        long usages = countUsages(uuid);
        // physical=true is only honored when truly unused — never trust the client
        // flag blindly, to avoid violating the FK or losing referenced data on a
        // race condition between the "usage" ping and this call.
        if (physical && usages == 0) {
            repository.delete(o);
            return;
        }
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
