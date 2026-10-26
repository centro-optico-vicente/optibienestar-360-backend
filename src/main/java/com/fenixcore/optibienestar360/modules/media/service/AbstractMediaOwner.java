package com.fenixcore.optibienestar360.modules.media.service;

import com.fenixcore.optibienestar360.modules.media.MediaAction;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

/** Shared permission helpers for {@link MediaOwner}s. */
public abstract class AbstractMediaOwner implements MediaOwner {

    /** Permission an admin needs per action; the owner supplies its own names. */
    protected static void requireAdmin(CustomUserDetails actor, Map<MediaAction, String> permissions, MediaAction action) {
        String needed = permissions.get(action);
        boolean allowed = actor != null && actor.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(needed));
        if (!allowed) {
            throw new AccessDeniedException("media.forbidden");
        }
    }
}
