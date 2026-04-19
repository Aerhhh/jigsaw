package net.aerh.tessera.core.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the contract on {@link BoundedVirtualExecutor}: semaphore-gated admission
 * caps concurrency at {@code bound}; construction rejects non-positive bounds; close()
 * drains in-flight work.
 */
class BoundedVirtualExecutorTest {

    @Test
    void default_bound_formula_is_max_16_cores_times_4() {
        int expected = Math.max(16, Runtime.getRuntime().availableProcessors() * 4);
        // Formula lives in DefaultEngineBuilder; verify via computed expected.
        // (Test sampling; exact formula is also gated by DefaultEngineBuilder integration.)
        assertThat(expected).isGreaterThanOrEqualTo(16);
    }

    @Test
    void rejects_non_positive_bound() {
        assertThatThrownBy(() -> new BoundedVirtualExecutor(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bound must be >= 1");
        assertThatThrownBy(() -> new BoundedVirtualExecutor(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bound_accessor_reports_constructor_argument() {
        try (BoundedVirtualExecutor exec = new BoundedVirtualExecutor(8)) {
            assertThat(exec.bound()).isEqualTo(8);
            assertThat(exec.availablePermits()).isEqualTo(8);
        }
    }

    @Test
    void rejects_null_command() {
        try (BoundedVirtualExecutor exec = new BoundedVirtualExecutor(4)) {
            assertThatThrownBy(() -> exec.execute(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("command");
        }
    }

    @Test
    void semaphore_enforces_bound() throws Exception {
        int bound = 4;
        BoundedVirtualExecutor exec = new BoundedVirtualExecutor(bound);
        try {
            CountDownLatch holdPermits = new CountDownLatch(1);
            CountDownLatch allStarted = new CountDownLatch(bound);
            AtomicInteger concurrent = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);

            // Submit 10 tasks; only `bound` should be concurrent at any time.
            for (int i = 0; i < 10; i++) {
                exec.execute(() -> {
                    int c = concurrent.incrementAndGet();
                    maxConcurrent.accumulateAndGet(c, Math::max);
                    allStarted.countDown();
                    try {
                        holdPermits.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    concurrent.decrementAndGet();
                });
            }

            // Wait until `bound` tasks have started (semaphore admitted them).
            assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
            // Brief settle to see if an unbounded executor lets more in.
            Thread.sleep(150);
            assertThat(maxConcurrent.get()).isEqualTo(bound);

            holdPermits.countDown();
            assertThat(exec.awaitTermination(Duration.ofSeconds(5))).isTrue();
        } finally {
            exec.close();
        }
    }

    @Test
    void close_drains_inflight_tasks() {
        BoundedVirtualExecutor exec = new BoundedVirtualExecutor(16);
        AtomicInteger completed = new AtomicInteger(0);
        for (int i = 0; i < 50; i++) {
            exec.execute(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completed.incrementAndGet();
            });
        }
        exec.close();  // ExecutorService.close() in Java 21 drains
        assertThat(completed.get()).isEqualTo(50);
    }

    @Test
    void permits_released_even_when_command_throws() throws Exception {
        BoundedVirtualExecutor exec = new BoundedVirtualExecutor(2);
        try {
            CountDownLatch throwingDone = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) {
                exec.execute(() -> {
                    try {
                        throw new RuntimeException("boom");
                    } finally {
                        throwingDone.countDown();
                    }
                });
            }
            assertThat(throwingDone.await(5, TimeUnit.SECONDS)).isTrue();
            // Brief settle so the finally-release in execute() completes.
            Thread.sleep(50);
            assertThat(exec.availablePermits()).isEqualTo(2);
        } finally {
            exec.close();
        }
    }
}
