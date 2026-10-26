package com.fenixcore.optibienestar360.modules.ally.media;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
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

/** The gallery of an ally (its logo and photos). Admin: {@code ALLY_*} permissions; portal: membership. */
@Component
@RequiredArgsConstructor
public class AllyMediaOwner extends AbstractMediaOwner {

    public static final String KEY = "ALLY";

    private static final Map<MediaAction, String> PERMISSIONS = Map.of(
            MediaAction.READ, "ALLY_VIEW_ALL",
            MediaAction.UPLOAD, "ALLY_IMAGE_UPLOAD",
            MediaAction.UPDATE, "ALLY_IMAGE_UPDATE",
            MediaAction.DELETE, "ALLY_IMAGE_DELETE");

    private final AllyRepository allyRepository;
    private final AllyPortalAccess portalAccess;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String ownerTable() {
        return MediaTables.ALLIES;
    }

    @Override
    public void requireExists(UUID ownerUuid, UUID parentUuid) {
        if (allyRepository.findByUuid(ownerUuid).isEmpty()) {
            throw new NoSuchElementException("ally.not_found");
        }
    }

    @Override
    public String logoUrl(UUID ownerUuid) {
        return allyRepository.findByUuid(ownerUuid).map(Ally::getLogoUrl).orElse(null);
    }

    @Override
    public void setLogoUrl(UUID ownerUuid, String url) {
        allyRepository.findByUuid(ownerUuid).ifPresent(ally -> ally.setLogoUrl(url));
    }

    @Override
    public void authorize(MediaAction action, MediaTarget target, CustomUserDetails actor) {
        if (target.scope() == MediaScope.PORTAL) {
            portalAccess.require(target.ownerUuid(), actor, action);
        } else {
            requireAdmin(actor, PERMISSIONS, action);
        }
    }
}
