package com.fenixcore.optibienestar360.modules.ally.media;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.media.MediaAction;
import com.fenixcore.optibienestar360.modules.media.MediaScope;
import com.fenixcore.optibienestar360.modules.media.MediaTables;
import com.fenixcore.optibienestar360.modules.media.MediaTarget;
import com.fenixcore.optibienestar360.modules.media.service.AbstractMediaOwner;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The gallery of one ally's offer of a product. When an offer has no images the
 * public pages show its product's (hub ADR 0027). Admin: {@code ALLY_PRODUCT_*}
 * permissions; portal: membership in the offer's ally.
 */
@Component
@RequiredArgsConstructor
public class AllyProductMediaOwner extends AbstractMediaOwner {

    public static final String KEY = "ALLY_PRODUCT";

    private static final Map<MediaAction, String> PERMISSIONS = Map.of(
            MediaAction.READ, "ALLY_PRODUCT_VIEW_ALL",
            MediaAction.UPLOAD, "ALLY_PRODUCT_IMAGE_UPLOAD",
            MediaAction.UPDATE, "ALLY_PRODUCT_IMAGE_UPDATE",
            MediaAction.DELETE, "ALLY_PRODUCT_IMAGE_DELETE");

    private final AllyProductRepository offerRepository;
    private final AllyPortalAccess portalAccess;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String ownerTable() {
        return MediaTables.ALLY_PRODUCTS;
    }

    @Override
    public void requireExists(UUID ownerUuid, UUID parentUuid) {
        AllyProduct offer = find(ownerUuid);
        // /allies/{A}/products/{X}/media where X belongs to ally B must 404, not act on B.
        if (parentUuid != null && !offer.getAlly().getUuid().equals(parentUuid)) {
            throw new NoSuchElementException("ally_product.not_found");
        }
    }

    @Override
    public String logoUrl(UUID ownerUuid) {
        return offerRepository.findByUuid(ownerUuid).map(AllyProduct::getLogoUrl).orElse(null);
    }

    @Override
    public void setLogoUrl(UUID ownerUuid, String url) {
        offerRepository.findByUuid(ownerUuid).ifPresent(offer -> offer.setLogoUrl(url));
    }

    @Override
    public void authorize(MediaAction action, MediaTarget target, CustomUserDetails actor) {
        if (target.scope() == MediaScope.PORTAL) {
            portalAccess.require(find(target.ownerUuid()).getAlly().getUuid(), actor, action);
        } else {
            requireAdmin(actor, PERMISSIONS, action);
        }
    }

    private AllyProduct find(UUID offerUuid) {
        return offerRepository.findByUuid(offerUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_product.not_found"));
    }
}
