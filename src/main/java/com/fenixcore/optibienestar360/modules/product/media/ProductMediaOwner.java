package com.fenixcore.optibienestar360.modules.product.media;

import com.fenixcore.optibienestar360.modules.media.MediaAction;
import com.fenixcore.optibienestar360.modules.media.MediaScope;
import com.fenixcore.optibienestar360.modules.media.MediaTables;
import com.fenixcore.optibienestar360.modules.media.MediaTarget;
import com.fenixcore.optibienestar360.modules.media.service.AbstractMediaOwner;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.repository.ProductRepository;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The gallery of a catalog product — shared by every ally offering it, unless
 * an offer brings its own images. Admin only ({@code PRODUCT_*} permissions):
 * allies manage their offers' galleries, not the shared product's.
 */
@Component
@RequiredArgsConstructor
public class ProductMediaOwner extends AbstractMediaOwner {

    public static final String KEY = "PRODUCT";

    private static final Map<MediaAction, String> PERMISSIONS = Map.of(
            MediaAction.READ, "PRODUCT_VIEW_ALL",
            MediaAction.UPLOAD, "PRODUCT_IMAGE_UPLOAD",
            MediaAction.UPDATE, "PRODUCT_IMAGE_UPDATE",
            MediaAction.DELETE, "PRODUCT_IMAGE_DELETE");

    private final ProductRepository productRepository;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String ownerTable() {
        return MediaTables.PRODUCTS;
    }

    @Override
    public void requireExists(UUID ownerUuid, UUID parentUuid) {
        if (productRepository.findByUuid(ownerUuid).isEmpty()) {
            throw new NoSuchElementException("product.not_found");
        }
    }

    @Override
    public String logoUrl(UUID ownerUuid) {
        return productRepository.findByUuid(ownerUuid).map(Product::getLogoUrl).orElse(null);
    }

    @Override
    public void setLogoUrl(UUID ownerUuid, String url) {
        productRepository.findByUuid(ownerUuid).ifPresent(product -> product.setLogoUrl(url));
    }

    @Override
    public void authorize(MediaAction action, MediaTarget target, CustomUserDetails actor) {
        if (target.scope() == MediaScope.PORTAL) {
            throw new AccessDeniedException("media.forbidden");
        }
        requireAdmin(actor, PERMISSIONS, action);
    }
}
