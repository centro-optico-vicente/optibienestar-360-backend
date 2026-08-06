package com.fenixcore.optibienestar360.core.util;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the {@code List<OptionDto>} response shared by every {@code /options}
 * endpoint (select/dropdown population). Encapsulates the two rules every such
 * endpoint must follow: the result set respects {@code q}/{@code limit} (capped
 * at 200 regardless of what the caller requested), and every uuid in
 * {@code currentValues} is guaranteed to appear — even inactive or outside the
 * base filter — so a `<select>` can render an already-assigned value without
 * hiding or blanking it.
 *
 * <p>No new repository methods are needed: {@code currentValues} is a short
 * list, so each uuid is resolved individually via the {@code findByUuid}
 * already exposed by every repository (ADR 0006), instead of adding a
 * {@code findByUuidIn}.</p>
 */
public final class OptionsSupport {

    private static final int MAX_LIMIT = 200;

    private OptionsSupport() {}

    public static <T> List<OptionDto> build(
            JpaSpecificationExecutor<T> specExecutor,
            Function<UUID, Optional<T>> findByUuid,
            Specification<T> baseSpec,
            List<UUID> currentValues,
            int limit,
            Function<T, UUID> uuidOf,
            Function<T, String> codeOf,
            Function<T, String> labelOf,
            Function<T, Boolean> activeOf) {

        int cappedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        // LinkedHashMap keeps base-result order first, currentValues appended after.
        Map<UUID, OptionDto> byUuid = new LinkedHashMap<>();
        for (T entity : specExecutor.findAll(baseSpec, PageRequest.of(0, cappedLimit)).getContent()) {
            OptionDto dto = toDto(entity, uuidOf, codeOf, labelOf, activeOf);
            byUuid.put(dto.uuid(), dto);
        }

        if (currentValues != null) {
            for (UUID uuid : currentValues) {
                if (uuid == null || byUuid.containsKey(uuid)) continue;
                findByUuid.apply(uuid).ifPresent(entity ->
                        byUuid.put(uuid, toDto(entity, uuidOf, codeOf, labelOf, activeOf)));
            }
        }

        return new ArrayList<>(byUuid.values());
    }

    private static <T> OptionDto toDto(T entity,
            Function<T, UUID> uuidOf, Function<T, String> codeOf,
            Function<T, String> labelOf, Function<T, Boolean> activeOf) {
        return new OptionDto(uuidOf.apply(entity), codeOf.apply(entity),
                labelOf.apply(entity), Boolean.TRUE.equals(activeOf.apply(entity)));
    }
}
