package com.fenixcore.optibienestar360.core.audit.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * Response for {@code GET /v1/admin/audit/data-changes} — same field set
 * Spring Data's {@link Page} already serializes ({@code content}, {@code
 * pageable}, {@code last}, {@code totalElements}, {@code totalPages}, {@code
 * size}, {@code number}, {@code first}, {@code sort}, {@code
 * numberOfElements}, {@code empty}), flattened into one record plus {@code
 * firstChange} — one request instead of two.
 *
 * <p>Mirrors {@code ListEntityLogsResponse.first_entity_log} from the
 * adempiere-grpc-server {@code logs.proto}: the list's own creation row
 * travels alongside the page, not as a separate endpoint — a record with
 * 500+ changes still lets the client show "created on ..." without paging to
 * the end. {@code firstChange} is only populated when the request scoped the
 * query to one record ({@code entityKey} + {@code entityUuid}); it's {@code
 * null} for the cross-entity, unscoped listing where "the first change of
 * what?" doesn't apply.</p>
 */
public record DataChangeAuditLogPageDto(
        List<DataChangeAuditLogDto> content,
        Pageable pageable,
        boolean last,
        long totalElements,
        int totalPages,
        int size,
        int number,
        boolean first,
        Sort sort,
        int numberOfElements,
        boolean empty,
        DataChangeAuditLogDto firstChange
) {
    public static DataChangeAuditLogPageDto of(Page<DataChangeAuditLogDto> page, DataChangeAuditLogDto firstChange) {
        return new DataChangeAuditLogPageDto(
                page.getContent(),
                page.getPageable(),
                page.isLast(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber(),
                page.isFirst(),
                page.getSort(),
                page.getNumberOfElements(),
                page.isEmpty(),
                firstChange
        );
    }
}
