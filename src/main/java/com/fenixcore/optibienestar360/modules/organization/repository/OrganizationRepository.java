package com.fenixcore.optibienestar360.modules.organization.repository;

import com.fenixcore.optibienestar360.core.util.TransactionScopedMemo;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByUuid(UUID uuid);

    List<Organization> findByActiveTrue();

    /**
     * Single-row convenience — {@code organizations} only ever has one active
     * tenant today (V86 seed). Multi-tenant support, if it ever lands, adds a
     * proper tenant-resolution path instead of widening this method.
     *
     * <p>Memoized per transaction ({@link TransactionScopedMemo}): conversion
     * enrichment calls it once per listed row (ConversionEnricher), and a page
     * of 25 commissions used to run the same query 25 times.</p>
     */
    default Organization findSingleton() {
        return TransactionScopedMemo.get(SINGLETON_MEMO_KEY, () -> findByActiveTrue().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("organization.not_configured")));
    }

    /** {@link TransactionScopedMemo} key of {@link #findSingleton()}. */
    String SINGLETON_MEMO_KEY = "organization:singleton";
}
