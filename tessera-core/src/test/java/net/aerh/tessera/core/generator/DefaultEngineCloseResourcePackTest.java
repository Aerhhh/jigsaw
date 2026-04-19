package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.assets.Capabilities;
import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.image.OutputSizeGate;
import net.aerh.tessera.api.nbt.NbtParser;
import net.aerh.tessera.api.overlay.OverlayColorProvider;
import net.aerh.tessera.api.resource.PackMetadata;
import net.aerh.tessera.api.resource.ResourcePack;
import net.aerh.tessera.api.sprite.SpriteProvider;
import net.aerh.tessera.core.nbt.DefaultNbtParser;
import net.aerh.tessera.core.nbt.handler.DefaultNbtFormatHandler;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies close() step 4 (ResourcePack close in reverse order) + step 5 (HttpClient
 * close). These steps were no-op stubs in an earlier version before {@link DefaultEngine}
 * owned either handle.
 *
 * <p>Uses a hand-rolled {@code RecordingResourcePack} (not Mockito) because Mockito 5.x
 * cannot rewrite JDK 21+ sealed types without workarounds, and a small recorder is
 * clearer than test-only framework plumbing. Same approach as
 * {@code SkinLoaderUsernameTest}'s FakeHttpClient.
 */
final class DefaultEngineCloseResourcePackTest {

    @Test
    void close_invokes_resource_pack_close_in_reverse_order() {
        RecordingResourcePack inner = new RecordingResourcePack("inner");
        RecordingResourcePack outer = new RecordingResourcePack("outer");
        List<String> closeOrder = new ArrayList<>();
        inner.onClose(() -> closeOrder.add("inner"));
        outer.onClose(() -> closeOrder.add("outer"));

        DefaultEngine engine = newEngineWithPacks(List.of(inner, outer));

        engine.close();

        assertThat(inner.closeCount()).isEqualTo(1);
        assertThat(outer.closeCount()).isEqualTo(1);
        // Reverse-order step 4 ("innermost first" means the last-registered
        // pack closes first - packs[size-1] is "outer" in our List.of(inner, outer)
        // ordering, so outer closes before inner).
        assertThat(closeOrder).containsExactly("outer", "inner");
    }

    @Test
    void close_logs_and_swallows_pack_close_exception() {
        RecordingResourcePack pack = new RecordingResourcePack("explodes");
        pack.setCloseException(new IOException("boom"));

        DefaultEngine engine = newEngineWithPacks(List.of(pack));

        // A misbehaving pack.close() MUST NOT propagate out of engine.close().
        assertThatCode(engine::close).doesNotThrowAnyException();
        assertThat(pack.closeCount()).isEqualTo(1);
    }

    @Test
    void close_is_idempotent_across_pack_close() {
        RecordingResourcePack pack = new RecordingResourcePack("once");
        DefaultEngine engine = newEngineWithPacks(List.of(pack));

        engine.close();
        engine.close();  // second call must not re-close the pack ( CAS idempotency)
        engine.close();

        assertThat(pack.closeCount()).isEqualTo(1);
    }

    @Test
    void close_continues_even_when_first_pack_fails() {
        RecordingResourcePack first = new RecordingResourcePack("first");
        RecordingResourcePack second = new RecordingResourcePack("second");
        first.setCloseException(new IOException("first-failed"));

        DefaultEngine engine = newEngineWithPacks(List.of(first, second));

        assertThatCode(engine::close).doesNotThrowAnyException();
        // Even though "second" closes first (reverse order) + first throws, second
        // must still have been closed.
        assertThat(first.closeCount()).isEqualTo(1);
        assertThat(second.closeCount()).isEqualTo(1);
    }

    @Test
    void close_closes_http_client() {
        RecordingHttpClient http = new RecordingHttpClient();
        DefaultEngine engine = newEngineWithPacksAndHttp(List.of(), http);

        engine.close();

        assertThat(http.closeCount()).isEqualTo(1);
    }

    // : HttpClient.close() is dispatched to a daemon thread with a bounded timeout
    // derived from shutdownTimeout. A misbehaving close() that blocks for 5 minutes must
    // NOT keep engine.close() hanging past the engine's advertised shutdown window.
    @Test
    void close_bounded_by_shutdown_timeout_when_http_close_hangs() {
        BlockingCloseHttpClient http = new BlockingCloseHttpClient();
        DefaultEngine engine = newEngineWithPacksAndHttp(List.of(), http);

        // newEngineWithPacksAndHttp builds the engine with shutdownTimeout=100ms.
        // If isn't applied, this test would wait up to 3 minutes (JDK default).
        long start = System.nanoTime();
        engine.close();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // 100ms timeout + some slack for thread scheduling + the executor-close step.
        // The key assertion: we did NOT wait 3 minutes.
        assertThat(elapsedMs).isLessThan(5_000L);
        assertThat(http.closeCalled()).isTrue();
        // Release the blocked close() so the daemon thread can exit cleanly.
        http.release();
    }

    // -----------------------------------------------------------------------
    // Plumbing: minimal DefaultEngine builder (no asset cache needed)
    // -----------------------------------------------------------------------

    private static DefaultEngine newEngineWithPacks(List<ResourcePack> packs) {
        return newEngineWithPacksAndHttp(packs, new RecordingHttpClient());
    }

    private static DefaultEngine newEngineWithPacksAndHttp(
            List<ResourcePack> packs, HttpClient http) {
        NbtParser parser = new DefaultNbtParser(List.of(new DefaultNbtFormatHandler()));
        return new DefaultEngine(
                stubSpriteProvider(),
                stubGenerator(ItemRequest.class),
                stubGenerator(TooltipRequest.class),
                stubGenerator(InventoryRequest.class),
                stubGenerator(PlayerHeadRequest.class),
                stubGenerator(PlayerModelRequest.class),
                parser,
                Map.<String, DataRegistry<?>>of(),
                OverlayColorProvider.fromDefaults(),
                new Capabilities(true, true, true, "test"),
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "close-test-executor");
                    t.setDaemon(true);
                    return t;
                }),
                false,
                Duration.ofMillis(100),
                packs,
                http,
                OutputSizeGate.DEFAULT_STATIC_CAP,
                OutputSizeGate.DEFAULT_ANIMATED_CAP);
    }

    private static <I> Generator<I, GeneratorResult> stubGenerator(Class<I> inputType) {
        return new Generator<>() {
            @Override
            public GeneratorResult render(I input, net.aerh.tessera.api.generator.GenerationContext ctx) {
                throw new UnsupportedOperationException("stub generator for " + inputType);
            }

            @Override
            public Class<I> inputType() {
                return inputType;
            }

            @Override
            public Class<GeneratorResult> outputType() {
                return GeneratorResult.class;
            }
        };
    }

    private static SpriteProvider stubSpriteProvider() {
        return new SpriteProvider() {
            @Override public Optional<BufferedImage> getSprite(String id) { return Optional.empty(); }
            @Override public Collection<String> availableSprites() { return Collections.emptyList(); }
            @Override public Optional<BufferedImage> search(String q) { return Optional.empty(); }
            @Override public List<Map.Entry<String, BufferedImage>> searchAll(String q) { return Collections.emptyList(); }
            @Override public Map<String, BufferedImage> getAllSprites() { return Collections.emptyMap(); }
        };
    }

    // -----------------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------------

    private static final class RecordingResourcePack implements ResourcePack {
        private final String label;
        private final AtomicInteger closeCount = new AtomicInteger();
        private IOException closeException;
        private Runnable onClose;

        RecordingResourcePack(String label) {
            this.label = label;
        }

        void setCloseException(IOException e) { this.closeException = e; }
        void onClose(Runnable hook) { this.onClose = hook; }
        int closeCount() { return closeCount.get(); }

        @Override public Optional<InputStream> getResource(String path) { return Optional.empty(); }
        @Override public boolean hasResource(String path) { return false; }
        @Override public Set<String> listResources(String prefix) { return Collections.emptySet(); }
        @Override public PackMetadata metadata() { return new PackMetadata(0, label); }

        @Override
        public void close() throws IOException {
            closeCount.incrementAndGet();
            if (onClose != null) onClose.run();
            if (closeException != null) throw closeException;
        }
    }

    /**
     * Minimal {@link HttpClient} subclass that records {@code close()} invocations.
     * Leaves every other method returning sensible defaults - we never call them in
     * the close-lifecycle tests.
     */
    private static final class RecordingHttpClient extends HttpClient {
        private final AtomicInteger closeCount = new AtomicInteger();

        int closeCount() { return closeCount.get(); }

        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() {
            try { return javax.net.ssl.SSLContext.getDefault(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }

        @Override
        public <T> java.net.http.HttpResponse<T> send(java.net.http.HttpRequest req,
                                                     java.net.http.HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest req, java.net.http.HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest req,
                java.net.http.HttpResponse.BodyHandler<T> handler,
                java.net.http.HttpResponse.PushPromiseHandler<T> pushHandler) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    /**
     *  test double: close() blocks on a latch until {@link #release()} is called.
     * Simulates the JDK-default 3-minute HttpClient.close() wait without actually
     * sleeping the test for 3 minutes.
     */
    private static final class BlockingCloseHttpClient extends HttpClient {
        private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicBoolean closeCalled = new java.util.concurrent.atomic.AtomicBoolean();

        boolean closeCalled() { return closeCalled.get(); }
        void release() { release.countDown(); }

        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() {
            try { return javax.net.ssl.SSLContext.getDefault(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }

        @Override
        public <T> java.net.http.HttpResponse<T> send(java.net.http.HttpRequest req,
                                                     java.net.http.HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest req, java.net.http.HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest req,
                java.net.http.HttpResponse.BodyHandler<T> handler,
                java.net.http.HttpResponse.PushPromiseHandler<T> pushHandler) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public void close() {
            closeCalled.set(true);
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
