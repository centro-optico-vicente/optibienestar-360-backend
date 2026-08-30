package com.fenixcore.optibienestar360.core.display;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Builds a {@link DisplayRef} from a loaded association entity, by convention
 * (reflection): {@code getUuid()} + optional {@code getCode()} + the first of
 * {@code getFullName() / getDisplayName() / getName() / getInstitutionName()}.
 *
 * <p>Stateless utility with {@code static} methods so mappers can pull it in
 * via {@code @Mapper(uses = DisplayRefs.class)} (MapStruct calls it
 * statically — no bean, no field injection, works in plain unit tests). A
 * {@code SomeEntity → DisplayRef} target mapping then resolves with no
 * per-mapper boilerplate. Person-shaped relations (labelled by document
 * number + full name from a nested {@code Person}) get an explicit typed
 * overload.</p>
 *
 * <p>The reflective order matches
 * {@code GenericEntityExtractorService.extractDisplayStringFromObject} and
 * {@code AuditDisplayResolver} so a label reads the same in a list, a detail
 * view, an audit snapshot and a report.</p>
 */
@Slf4j
public final class DisplayRefs {

    private static final String[] NAME_GETTERS = {
            "getFullName", "getDisplayName", "getName", "getInstitutionName"
    };

    private DisplayRefs() {
    }

    /** Person-shaped: document number in {@code code}, full name in {@code name}. */
    public static DisplayRef ref(Person person) {
        return person == null ? null
                : DisplayRef.of(person.getUuid(), person.getTaxDocumentNumber(), person.getFullName());
    }

    /** A user is labelled by its login email. */
    public static DisplayRef ref(User user) {
        return user == null ? null : DisplayRef.of(user.getUuid(), null, user.getEmail());
    }

    /** Reflective fallback — works for catalogs, plan, ally, promoter, contract, tiers… */
    public static DisplayRef ref(Object entity) {
        if (entity == null) {
            return null;
        }
        UUID uuid = call(entity, "getUuid") instanceof UUID u ? u : null;
        String code = str(call(entity, "getCode"));
        String name = null;
        for (String getter : NAME_GETTERS) {
            name = str(call(entity, getter));
            if (name != null) {
                break;
            }
        }
        return DisplayRef.of(uuid, code, name);
    }

    private static Object call(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            return m.invoke(target);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (ReflectiveOperationException e) {
            log.debug("DisplayRefs.{} failed on {}", getter, target.getClass().getSimpleName(), e);
            return null;
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
