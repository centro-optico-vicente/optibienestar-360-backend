package com.fenixcore.optibienestar360.modules.media;

import com.fenixcore.optibienestar360.modules.media.service.MediaOwner;

import java.util.UUID;

/**
 * The gallery a request acts on.
 *
 * @param parentUuid the parent named in the URL when the owner is nested
 *                   ({@code allyUuid} of an offer), so a tampered URL answers 404; {@code null} otherwise
 */
public record MediaTarget(MediaOwner owner, UUID ownerUuid, MediaScope scope, UUID parentUuid) {
}
