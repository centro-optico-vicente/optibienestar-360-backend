package com.fenixcore.optibienestar360.modules.media;

/** Which surface the request came through; decides how the owner authorizes it. */
public enum MediaScope {
    /** {@code /v1/admin/**}: granular permissions. */
    ADMIN,
    /** {@code /v1/me/**}: the ally portal, membership based (OWNER/STAFF write, VIEWER reads). */
    PORTAL
}
