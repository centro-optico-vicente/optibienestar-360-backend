package com.fenixcore.optibienestar360.common.storage;

/**
 * Lifecycle of a {@link FileDownloadLink} — matches {@code chk_file_download_links_status} (V249).
 * Only {@code ACTIVE} can be used, and even then the use-time check still compares
 * {@code expires_at} with the current time: the status may lag until
 * {@code LINK_EXPIRY_SWEEP} runs.
 */
public enum DownloadLinkStatus {
    ACTIVE,
    /** Past {@code expires_at} — marked by the sweep or on the first hit after expiry. */
    EXPIRED,
    /** Revoked by hand; always paired with {@code revoked_at}. */
    REVOKED,
    /** {@code max_downloads} reached — marked by the same UPDATE that counts the last download. */
    EXHAUSTED
}
