package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.core.exception.ServiceBusyException;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Caps how many generic PDF documents render at the same time. Each render holds the parsed fonts
 * and the whole document in memory, and several in parallel multiply that peak inside the
 * container's memory limit.
 *
 * <p>The cap and the wait come from {@code system_configs} (V238) and are read on every call, so a
 * change applies to the next request without a restart. A request that cannot get a slot within
 * the wait gets {@link ServiceBusyException} (503 + {@code Retry-After}).
 */
@Component
public class PdfRenderLimiter {

    static final String BUSY_MESSAGE_CODE = "document.error.busy";
    static final int RETRY_AFTER_SECONDS = 10;

    private final SystemConfigService systemConfigService;
    private final Object lock = new Object();
    private int activeRenders;

    public PdfRenderLimiter(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /** Runs {@code render} once a slot is free, releasing the slot when it finishes or fails. */
    public <T> T run(Supplier<T> render) {
        acquire();
        try {
            return render.get();
        } finally {
            release();
        }
    }

    private void acquire() {
        int maxRenders = Math.max(1, systemConfigService.getPdfMaxConcurrentRenders());
        long waitNanos = TimeUnit.SECONDS.toNanos(Math.max(0, systemConfigService.getPdfRenderWaitSeconds()));
        long deadline = System.nanoTime() + waitNanos;

        synchronized (lock) {
            while (activeRenders >= maxRenders) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new ServiceBusyException(BUSY_MESSAGE_CODE, RETRY_AFTER_SECONDS);
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServiceBusyException(BUSY_MESSAGE_CODE, RETRY_AFTER_SECONDS);
                }
            }
            activeRenders++;
        }
    }

    private void release() {
        synchronized (lock) {
            activeRenders--;
            lock.notifyAll();
        }
    }
}
