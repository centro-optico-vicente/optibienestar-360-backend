package com.fenixcore.optibienestar360.contract;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyDetailDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionDto;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sample instances of the reference DTOs used to compare the OpenAPI spec with
 * what the real response serializer writes (ADR LOC-0015). Samples are
 * serialized with the Spring Boot Jackson 3 {@code JsonMapper}, never with the
 * mapper springdoc uses.
 */
final class WireSamples {

    static final String ALLY_LIST_ITEM = "AllyListItemDto";
    static final String MEMBER_DETAIL = "MemberDetailDto";
    static final String PUBLIC_ALLY_DETAIL = "PublicAllyDetailDto";
    /** Class-level {@code @JsonInclude(NON_NULL)}: null keys are omitted on the wire. */
    static final String COMMISSION_NON_NULL = "CommissionDto";

    private WireSamples() {
    }

    static Map<String, Object> all() throws Exception {
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put(ALLY_LIST_ITEM, new AllyListItemDto(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Optica Sample", 7, AllyType.Segment.B2B,
                List.of("Optica"),
                new DisplayRef(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), "CCS", "Caracas"),
                "J", "123456789", null, null,
                true, Instant.parse("2026-01-02T03:04:05Z"),
                true, "APPROVED", Instant.parse("2026-01-01T00:00:00Z")));
        samples.put("AppliedSortPage(empty)", new AppliedSortPage<String>(
                new PageImpl<String>(List.of(), PageRequest.of(0, 10), 0), List.of()));
        samples.put(MEMBER_DETAIL, blank(MemberDetailDto.class));
        samples.put(PUBLIC_ALLY_DETAIL, blank(PublicAllyDetailDto.class));
        samples.put(COMMISSION_NON_NULL, blank(CommissionDto.class));
        return samples;
    }

    /** Builds a record with default component values (null / 0 / false). */
    static Object blank(Class<?> recordType) throws Exception {
        RecordComponent[] components = recordType.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = defaultOf(types[i]);
        }
        Constructor<?> ctor = recordType.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(values);
    }

    private static Object defaultOf(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return 0;
    }

}
