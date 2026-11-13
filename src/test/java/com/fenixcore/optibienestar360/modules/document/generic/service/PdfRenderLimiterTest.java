package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.core.exception.ServiceBusyException;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfRenderLimiterTest {

    private SystemConfigService systemConfigService;
    private PdfRenderLimiter limiter;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        systemConfigService = mock(SystemConfigService.class);
        limiter = new PdfRenderLimiter(systemConfigService);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /** Starts a render that holds its slot until {@code release} is counted down. */
    private Future<String> holdSlot(CountDownLatch started, CountDownLatch release) {
        return executor.submit(() -> limiter.run(() -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "first";
        }));
    }

    @Test
    void rejectsWithServiceBusyWhenTheWaitRunsOut() throws Exception {
        when(systemConfigService.getPdfMaxConcurrentRenders()).thenReturn(1);
        when(systemConfigService.getPdfRenderWaitSeconds()).thenReturn(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<String> first = holdSlot(started, release);
        assertTrue(started.await(5, TimeUnit.SECONDS));

        ServiceBusyException busy = assertThrows(ServiceBusyException.class, () -> limiter.run(() -> "second"));
        assertEquals(PdfRenderLimiter.BUSY_MESSAGE_CODE, busy.getMessage());
        assertEquals(PdfRenderLimiter.RETRY_AFTER_SECONDS, busy.getRetryAfterSeconds());

        release.countDown();
        assertEquals("first", first.get(5, TimeUnit.SECONDS));
        assertEquals("third", limiter.run(() -> "third"), "the slot is free again after the first render");
    }

    @Test
    void waitsForAFreeSlotWithinTheConfiguredWait() throws Exception {
        when(systemConfigService.getPdfMaxConcurrentRenders()).thenReturn(1);
        when(systemConfigService.getPdfRenderWaitSeconds()).thenReturn(5);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        holdSlot(started, release);
        assertTrue(started.await(5, TimeUnit.SECONDS));

        Future<String> waiting = executor.submit(() -> limiter.run(() -> "second"));
        Thread.sleep(100);
        assertFalse(waiting.isDone(), "second render waits while the slot is taken");

        release.countDown();
        assertEquals("second", waiting.get(5, TimeUnit.SECONDS));
    }

    @Test
    void releasesTheSlotWhenTheRenderFails() {
        when(systemConfigService.getPdfMaxConcurrentRenders()).thenReturn(1);
        when(systemConfigService.getPdfRenderWaitSeconds()).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> limiter.run(() -> {
            throw new IllegalStateException("render failed");
        }));
        assertEquals("next", limiter.run(() -> "next"));
    }

    @Test
    void readsTheCapOnEveryRequest() throws Exception {
        when(systemConfigService.getPdfMaxConcurrentRenders()).thenReturn(1);
        when(systemConfigService.getPdfRenderWaitSeconds()).thenReturn(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        holdSlot(started, release);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertThrows(ServiceBusyException.class, () -> limiter.run(() -> "blocked"));

        when(systemConfigService.getPdfMaxConcurrentRenders()).thenReturn(2);
        assertEquals("allowed", assertTimeoutPreemptively(Duration.ofSeconds(5), () -> limiter.run(() -> "allowed")),
                "raising the cap in system_configs applies to the next request");
        release.countDown();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void capOfZeroOrMinusOneRendersWithoutLimit(int cap) throws Exception {
        when(systemConfigService.getPdfMaxConcurrentRenders()).thenReturn(cap);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        holdSlot(started, release);
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertEquals("second", assertTimeoutPreemptively(Duration.ofSeconds(2), () -> limiter.run(() -> "second")),
                "no cap: a second render runs while the first is still rendering");
        release.countDown();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void waitOfZeroOrMinusOneWaitsWithoutTimeLimit(int wait) throws Exception {
        when(systemConfigService.getPdfMaxConcurrentRenders()).thenReturn(1);
        when(systemConfigService.getPdfRenderWaitSeconds()).thenReturn(wait);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        holdSlot(started, release);
        assertTrue(started.await(5, TimeUnit.SECONDS));

        Future<String> waiting = executor.submit(() -> limiter.run(() -> "second"));
        Thread.sleep(1500);
        assertFalse(waiting.isDone(), "still waiting, no 503 for running out of time");

        release.countDown();
        assertEquals("second", waiting.get(5, TimeUnit.SECONDS));
    }
}
