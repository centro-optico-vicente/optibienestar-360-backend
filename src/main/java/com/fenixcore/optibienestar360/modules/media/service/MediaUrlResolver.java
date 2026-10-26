package com.fenixcore.optibienestar360.modules.media.service;

import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The URL clients load an image from: the external URL as given, or
 * {@code publicBaseUrl + publicKey} for an uploaded file — derived at read time
 * so a change of public domain never rewrites {@code media_items} (only the
 * denormalized {@code logo_url} columns, see {@code MediaLogoUrlSyncRunner}).
 */
@Component
public class MediaUrlResolver {

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    public String url(MediaItem item) {
        if (item.getExternalUrl() != null) {
            return item.getExternalUrl();
        }
        if (item.getPublicKey() == null || publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        return publicBaseUrl.endsWith("/") ? publicBaseUrl + item.getPublicKey() : publicBaseUrl + "/" + item.getPublicKey();
    }
}
