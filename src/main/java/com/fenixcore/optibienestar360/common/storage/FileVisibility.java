package com.fenixcore.optibienestar360.common.storage;

/**
 * Which R2 bucket an object lives in and how it may be accessed — spec
 * {@code .ai/specs/08-storage-r2.md} §1.
 *
 * <p>{@link #PUBLIC} lives in the public bucket, served directly by URL, no
 * presigning. The other three live in the private bucket behind a presigned
 * URL: {@link #CONFIDENTIAL} for personal documents with an owner,
 * {@link #INTERNAL} for staff-only content with no personal-owner semantics
 * (e.g. a draft catalog image awaiting approval), {@link #TEMPORARY} for
 * short-lived content (reserved for the future reporting engine, ADR 0012 —
 * no consumer yet).</p>
 */
public enum FileVisibility {
    PUBLIC,
    TEMPORARY,
    INTERNAL,
    CONFIDENTIAL;

    /** Lowercase form used as the first path segment of a storage key. */
    public String keySegment() {
        return name().toLowerCase();
    }
}
