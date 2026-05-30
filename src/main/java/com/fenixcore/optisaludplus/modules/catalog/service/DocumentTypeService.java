package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.DocumentType;
import com.fenixcore.optisaludplus.modules.catalog.repository.DocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentTypeService {

    private final DocumentTypeRepository repository;

    public List<DocumentTypeDto> list() {
        return repository.findAllByActiveTrueOrderByName().stream().map(DocumentTypeService::toDto).toList();
    }

    public DocumentTypeDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    public DocumentTypeDto create(DocumentTypeCreateRequest req) {
        DocumentType d = new DocumentType();
        d.setCode(req.code());
        d.setName(req.name());
        d.setDescription(req.description());
        return toDto(repository.save(d));
    }

    @Transactional
    public DocumentTypeDto update(UUID uuid, DocumentTypeUpdateRequest req) {
        DocumentType d = find(uuid);
        d.setName(req.name());
        d.setDescription(req.description());
        return toDto(repository.save(d));
    }

    @Transactional
    public void delete(UUID uuid) {
        DocumentType d = find(uuid);
        d.setActive(false);
        repository.save(d);
    }

    private DocumentType find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("DocumentType not found: " + uuid));
    }

    static DocumentTypeDto toDto(DocumentType d) {
        return new DocumentTypeDto(d.getUuid(), d.getCode(), d.getName(), d.getDescription(), d.isActive());
    }
}
