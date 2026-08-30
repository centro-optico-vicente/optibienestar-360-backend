package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/me/allies} — "which allies do I operate
 * on?". Flattens the {@link com.fenixcore.optibienestar360.modules.ally.entity.AllyUser
 * AllyUser} pivot row plus its parent {@link
 * com.fenixcore.optibienestar360.modules.ally.entity.Ally Ally} into the
 * fields the partner portal needs to render a picker and then act.
 *
 * <p>{@code uuid} is the <b>ally</b> UUID, not the pivot's — that is the
 * value {@code POST /v1/ally/benefit-usage} and {@code POST /v1/aliado/services}
 * expect as {@code allyUuid}, and having the portal read it straight off
 * this record is the whole point of the endpoint.</p>
 *
 * <p>{@code allyType} serializes as {@code allyType_Uuid} +
 * {@code allyType_Display} ("code - name"); membership scalars carry their
 * {@code _Display} sibling (hub ADR 0014).</p>
 *
 * <p>{@code allyRole} is the caller's authority <i>inside</i> this ally, so
 * the portal can hide write actions from a VIEWER instead of letting them
 * fail with a 403 at the counter.</p>
 *
 * <p>{@code joinedAt} is when the <b>caller</b> joined the ally (the pivot's
 * column), not when the ally joined the program.</p>
 */
public record MyAllyDto(
        UUID uuid,
        String name,

        @Display DisplayRef allyType,

        String logoUrl,
        String phone,

        // The caller's membership in this ally.
        @Display(Display.Kind.ENUM) AllyRole allyRole,
        @Display(Display.Kind.BOOLEAN) boolean primary,
        @Display(Display.Kind.DATE) LocalDate joinedAt
) {}
