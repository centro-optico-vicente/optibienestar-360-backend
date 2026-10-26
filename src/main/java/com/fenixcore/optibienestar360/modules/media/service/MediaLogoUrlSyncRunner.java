package com.fenixcore.optibienestar360.modules.media.service;

import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Keeps the owners' {@code logo_url} columns equal to their primary image's URL
 * (hub ADR 0027). SQL cannot build the URL of an uploaded image — it needs
 * {@code publicBaseUrl} — so V200 leaves {@code products.logo_url} empty for the
 * migrated images and this runner fills it on startup.
 *
 * <p>Idempotent: by default it only fills owners that have a primary image but
 * no {@code logo_url}. With {@code storage.r2.media.resync-logo-urls=true} it
 * recomputes every owner — needed once after the public R2 domain changes,
 * because {@code logo_url} stores the full URL. Never blocks startup: a failure
 * (for example a database that is not migrated yet) is logged and skipped.</p>
 */
@Slf4j
@Component
public class MediaLogoUrlSyncRunner implements ApplicationRunner {

    private final MediaItemRepository repository;
    private final MediaOwnerRegistry owners;
    private final MediaUrlResolver urls;
    private final TransactionTemplate transactionTemplate;

    public MediaLogoUrlSyncRunner(MediaItemRepository repository, MediaOwnerRegistry owners,
                                  MediaUrlResolver urls, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.owners = owners;
        this.urls = urls;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Value("${storage.r2.media.resync-logo-urls:false}")
    private boolean resyncAll;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = transactionTemplate.execute(status -> sync());
            if (updated > 0) {
                log.info("media logo_url sync: {} owner(s) updated", updated);
            }
        } catch (RuntimeException e) {
            log.warn("media logo_url sync skipped: {}", e.getMessage());
        }
    }

    private int sync() {
        int updated = 0;
        List<MediaItem> primaries = repository.findByPrimaryTrueAndActiveTrue();
        for (MediaItem primary : primaries) {
            MediaOwner owner = owners.byTableOrNull(primary.getOwnerTable());
            String desired = urls.url(primary);
            if (owner == null || desired == null) {
                continue;
            }
            String current = owner.logoUrl(primary.getOwnerUuid());
            if (current == null || (resyncAll && !desired.equals(current))) {
                owner.setLogoUrl(primary.getOwnerUuid(), desired);
                updated++;
            }
        }
        return updated;
    }
}
