package net.aerh.tessera.core.engine;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Per-engine {@link Executor}: unlimited virtual threads via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}, bounded admission via a
 * {@link Semaphore} sized to {@code bound} permits.
 *
 * <p>Submission contract: every task acquires a permit before running, releases on
 * completion (including abnormal completion). The {@code (bound+1)}-th concurrent
 * task queues on the semaphore. Virtual threads are unlimited in count; admission
 * is bounded by the semaphore gate, providing backpressure semantics for async
 * render fan-out.
 *
 * <p>Permit leakage is impossible because (a) permits are acquired INSIDE the task
 * body (after the virtual thread starts), and (b) release is in a {@code finally}
 * block. Cancel-before-thread-start never acquired a permit, so there is nothing to leak.
 *
 * <p>This class is public-but-core-internal:   moved {@code DefaultEngine}
 * and {@code DefaultEngineBuilder} into {@code core.generator} so the built-in records
 * could flip package-private, which in turn required widening the admission-executor's
 * visibility so cross-package same-module callers could still reference it. Consumer-jar
 * isolation is preserved by {@code DownstreamArchUnitRules#noImportsFromTesseraCore}
 *. Consumers configure the admission bound via {@code EngineBuilder#executorBound(int)}.
 */
public final class BoundedVirtualExecutor implements Executor, AutoCloseable {

    private final ExecutorService delegate;
    private final Semaphore permits;
    private final int bound;

    /**
     * Creates a new bounded executor with {@code bound} concurrent-admission permits.
     *
     * @param bound the maximum number of tasks that may run concurrently; must be {@code >= 1}
     * @throws IllegalArgumentException if {@code bound < 1}
     */
    public BoundedVirtualExecutor(int bound) {
        if (bound < 1) {
            throw new IllegalArgumentException("bound must be >= 1, got: " + bound);
        }
        this.bound = bound;
        this.delegate = Executors.newVirtualThreadPerTaskExecutor();
        this.permits = new Semaphore(bound);
    }

    /** Returns the configured admission bound. */
    int bound() {
        return bound;
    }

    /** Returns the number of permits currently available (bound minus in-flight tasks). */
    int availablePermits() {
        return permits.availablePermits();
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command must not be null");
        delegate.execute(() -> {
            permits.acquireUninterruptibly();
            try {
                command.run();
            } finally {
                permits.release();
            }
        });
    }

    /**
     * Shuts the executor down and awaits termination up to {@code timeout}.
     *
     * @param timeout the maximum time to wait
     * @return {@code true} if the executor drained all in-flight tasks within the timeout;
     *         {@code false} if the timeout expired first
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if {@code timeout} is null
     */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout must not be null");
        delegate.shutdown();
        return delegate.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        // Unbounded close - drains whatever is still running per ExecutorService.close() in Java 21.
        delegate.close();
    }
}
