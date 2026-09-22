package com.github.bgalek.llm;

import org.slf4j.Logger;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Bounds simultaneous calls into the wrapped provider.
 * <p>
 * The llama.cpp box serves a fixed number of slots (--parallel N). Firing more requests than that
 * at it does not make anything faster, it just builds an invisible queue where every player waits
 * behind every other player - measured at 7s/14s/21s/28s for four concurrent calls against a
 * single-slot server. With virtual threads enabled there is nothing else holding this back.
 * <p>
 * This wraps the provider rather than living in MerlinService so that the second LLM call made by
 * the level 6/7 output filter is throttled too; those levels cost two calls per player turn.
 */
public class ThrottledLlmProvider implements LlmProvider {
    private static final Logger logger = getLogger(ThrottledLlmProvider.class);

    private final LlmProvider delegate;
    private final Semaphore semaphore;
    private final int waitSeconds;
    private final AtomicInteger waiting = new AtomicInteger();

    public ThrottledLlmProvider(LlmProvider delegate, int maxConcurrent, int waitSeconds) {
        this.delegate = delegate;
        // Fair, so a player who has been waiting is served before a request that just arrived.
        this.semaphore = new Semaphore(Math.max(1, maxConcurrent), true);
        this.waitSeconds = waitSeconds > 0 ? waitSeconds : 45;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        boolean acquired = false;
        waiting.incrementAndGet();
        try {
            acquired = semaphore.tryAcquire(waitSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                logger.warn("LLM saturated - gave up after {}s with {} callers waiting", waitSeconds, waiting.get());
                throw new LlmBusyException();
            }
            return delegate.chat(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmBusyException();
        } finally {
            waiting.decrementAndGet();
            if (acquired) semaphore.release();
        }
    }

    /** How many callers are currently queued or in flight - surfaced on the admin health endpoint. */
    public int getWaitingCount() {
        return waiting.get();
    }

    public int getAvailableSlots() {
        return semaphore.availablePermits();
    }
}
