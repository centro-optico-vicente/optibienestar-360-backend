package com.fenixcore.optibienestar360.modules.media.service;

import com.fenixcore.optibienestar360.modules.media.MediaAction;
import com.fenixcore.optibienestar360.modules.media.MediaTarget;
import com.fenixcore.optibienestar360.security.CustomUserDetails;

import java.util.UUID;

/**
 * An entity that owns a gallery (hub ADR 0027). One {@code @Component} per
 * owner, collected by {@link MediaOwnerRegistry}. Adding users, members or
 * advisors later = a new component + a {@code logo_url} column on their table
 * + their permissions; {@code media_items} and {@link MediaItemsService} stay
 * as they are.
 */
public interface MediaOwner {

    /** Stable key used by the controllers, e.g. {@code ALLY}. */
    String key();

    /** The owner's table — the {@code owner_table} of its {@code media_items}. */
    String ownerTable();

    /**
     * Fails with a 404 when the owner does not exist — or, for a nested owner,
     * does not belong to {@code parentUuid} (URL tampering).
     */
    void requireExists(UUID ownerUuid, UUID parentUuid);

    /** Current value of the owner's {@code logo_url} column ({@code null} when unset or unknown). */
    String logoUrl(UUID ownerUuid);

    /** Writes the owner's {@code logo_url}; only {@code MediaItemsService} calls this. */
    void setLogoUrl(UUID ownerUuid, String url);

    /** Throws {@code AccessDeniedException} unless {@code actor} may perform {@code action} on the target. */
    void authorize(MediaAction action, MediaTarget target, CustomUserDetails actor);
}
