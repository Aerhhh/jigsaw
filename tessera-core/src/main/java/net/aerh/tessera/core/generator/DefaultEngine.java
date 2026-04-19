package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.assets.Capabilities;
import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.data.RegistryKey;
import net.aerh.tessera.api.exception.ClosedEngineException;
import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RegistryException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.ValidationException;
import net.aerh.tessera.api.generator.CompositeBuilder;
import net.aerh.tessera.api.generator.FromNbtBuilder;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.InventoryBuilder;
import net.aerh.tessera.api.generator.ItemBuilder;
import net.aerh.tessera.api.generator.PlayerHeadBuilder;
import net.aerh.tessera.api.generator.PlayerModelBuilder;
import net.aerh.tessera.api.generator.RenderRequest;
import net.aerh.tessera.api.generator.TooltipBuilder;
import net.aerh.tessera.api.nbt.NbtParser;
import net.aerh.tessera.api.nbt.ParsedItem;
import net.aerh.tessera.api.overlay.OverlayColorProvider;
import net.aerh.tessera.api.resource.ResourcePack;
import net.aerh.tessera.api.sprite.SpriteProvider;
import net.aerh.tessera.core.cache.CachingGenerator;
import net.aerh.tessera.core.engine.BoundedVirtualExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Default implementation of {@link Engine} that wires together all built-in components
 * and dispatches render requests to the appropriate generator via an exhaustive sealed
 * switch over {@link CoreRenderRequest}.
 *
 * <p>Use {@link Engine#builder()} - which delegates via {@link java.util.ServiceLoader}
 * to {@link DefaultEngineBuilder} - to construct an instance.
 *
 * <p>Notable responsibilities:
 * <ul>
 *   <li>Owns a per-engine {@link BoundedVirtualExecutor} unless the consumer supplied an
 *       {@link Executor} via {@code EngineBuilder.executor(...)}.</li>
 *   <li>{@link #close()} executes a fixed six-step drain-and-release sequence; double-close is
 *       a no-op (CAS-idempotent); post-close render calls throw {@link ClosedEngineException}.</li>
 *   <li>Owns the engine-wide {@link HttpClient} used by player-head / player-model skin fetches
 *       so close() can drain and shut it down.</li>
 *   <li>Owns the user-supplied / vanilla-layered {@link ResourcePack} list (wrapped in
 *       {@link net.aerh.tessera.core.resource.LayeredResourcePack} when a custom pack is set)
 *       so close() can flush handles deterministically.</li>
 *   <li>Stores {@code staticOutputCapBytes} / {@code animatedOutputCapBytes} resolved at
 *       build time via {@link net.aerh.tessera.api.image.OutputSizeGate#resolveStaticCap}
 *       / {@code resolveAnimatedCap}; stamped onto {@code GeneratorResult.StaticImage} /
 *       {@code AnimatedImage} at dispatch time so the post-encode gate fires against the
 *       engine-resolved cap rather than falling through to env / default.</li>
 *   <li>External-consumer fallback registry is an empty immutable {@code Map.of()} placeholder;
 *       future work wires the Builder to accept custom {@link net.aerh.tessera.spi.GeneratorFactory}
 *       instances that populate it.</li>
 * </ul>
 *
 * @see Engine
 */
public final class DefaultEngine implements Engine {

    private static final Logger log = LoggerFactory.getLogger(DefaultEngine.class);

    private final SpriteProvider spriteProvider;
    private final Generator<ItemRequest, GeneratorResult> itemGenerator;
    private final Generator<TooltipRequest, GeneratorResult> tooltipGenerator;
    private final Generator<InventoryRequest, GeneratorResult> inventoryGenerator;
    private final Generator<PlayerHeadRequest, GeneratorResult> playerHeadGenerator;
    private final Generator<PlayerModelRequest, GeneratorResult> playerModelGenerator;
    private final NbtParser nbtParser;
    private final Map<String, DataRegistry<?>> registries;
    private final OverlayColorProvider overlayColorProvider;
    private final Capabilities capabilities;

    /** Per-engine executor. {@link #ownExecutor} governs whether close() shuts it down. */
    private final Executor executor;
    /** True when this engine owns its {@link #executor}; false when the consumer supplied one. */
    private final boolean ownExecutor;
    /** Drain timeout used by close(). Default 5s; configurable via {@code EngineBuilder.shutdownTimeout}. */
    private final Duration shutdownTimeout;

    /** ResourcePack handles drained by {@link #close()} in reverse order. */
    private final List<ResourcePack> resourcePacks;
    /** Shared HttpClient closed by {@link #close()} after the drain completes. */
    private final HttpClient httpClient;
    /** Post-encode cap stamped onto {@code StaticImage} at dispatch time. */
    private final long staticOutputCapBytes;
    /** Post-encode cap stamped onto {@code AnimatedImage} at dispatch time. */
    private final long animatedOutputCapBytes;

    /** CAS-idempotent close flag so double-close is a no-op. */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** Drain counter; incremented on render entry, decremented in {@code finally}. */
    private final AtomicInteger inflightRenders = new AtomicInteger(0);

    /** Placeholder; a future builder extension will populate this with custom GeneratorFactory instances. */
    @SuppressWarnings("rawtypes")
    private final Map<Class<? extends RenderRequest>, Generator> externalRegistry = Map.of();

    /**
     * Package-private constructor invoked by {@link DefaultEngineBuilder#build()}.
     * Immutable aside from {@link #closed} and {@link #inflightRenders}.
     */
    DefaultEngine(
            SpriteProvider spriteProvider,
            Generator<ItemRequest, GeneratorResult> itemGenerator,
            Generator<TooltipRequest, GeneratorResult> tooltipGenerator,
            Generator<InventoryRequest, GeneratorResult> inventoryGenerator,
            Generator<PlayerHeadRequest, GeneratorResult> playerHeadGenerator,
            Generator<PlayerModelRequest, GeneratorResult> playerModelGenerator,
            NbtParser nbtParser,
            Map<String, DataRegistry<?>> registries,
            OverlayColorProvider overlayColorProvider,
            Capabilities capabilities,
            Executor executor,
            boolean ownExecutor,
            Duration shutdownTimeout,
            List<ResourcePack> resourcePacks,
            HttpClient httpClient,
            long staticOutputCapBytes,
            long animatedOutputCapBytes
    ) {
        this.spriteProvider = spriteProvider;
        this.itemGenerator = itemGenerator;
        this.tooltipGenerator = tooltipGenerator;
        this.inventoryGenerator = inventoryGenerator;
        this.playerHeadGenerator = playerHeadGenerator;
        this.playerModelGenerator = playerModelGenerator;
        this.nbtParser = nbtParser;
        this.registries = Map.copyOf(registries);
        this.overlayColorProvider = overlayColorProvider;
        this.capabilities = capabilities;
        this.executor = executor;
        this.ownExecutor = ownExecutor;
        this.shutdownTimeout = shutdownTimeout;
        this.resourcePacks = List.copyOf(Objects.requireNonNull(resourcePacks, "resourcePacks must not be null"));
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        if (staticOutputCapBytes <= 0) {
            throw new IllegalArgumentException("staticOutputCapBytes must be > 0, got: " + staticOutputCapBytes);
        }
        if (animatedOutputCapBytes <= 0) {
            throw new IllegalArgumentException("animatedOutputCapBytes must be > 0, got: " + animatedOutputCapBytes);
        }
        this.staticOutputCapBytes = staticOutputCapBytes;
        this.animatedOutputCapBytes = animatedOutputCapBytes;
    }

    // -------------------------------------------------------------------------
    // Fluent entry points
    // -------------------------------------------------------------------------

    @Override
    public ItemBuilder item() {
        return new ItemBuilderImpl(this);
    }

    @Override
    public TooltipBuilder tooltip() {
        return new TooltipBuilderImpl(this);
    }

    @Override
    public InventoryBuilder inventory() {
        return new InventoryBuilderImpl(this);
    }

    @Override
    public PlayerHeadBuilder playerHead() {
        return new PlayerHeadBuilderImpl(this);
    }

    @Override
    public PlayerModelBuilder playerModel() {
        return new PlayerModelBuilderImpl(this);
    }

    @Override
    public CompositeBuilder composite() {
        return new CompositeBuilderImpl(this);
    }

    @Override
    public FromNbtBuilder fromNbt(String nbt) {
        return new FromNbtBuilderImpl(this, nbt);
    }

    /**
     * Returns this engine's {@link Executor}. Used by the same-package {@code *BuilderImpl}
     * classes to schedule async render terminals. Internal API - do NOT call from outside
     * Tessera.
     */
    public Executor executor() {
        return executor;
    }

    /**
     * Returns this engine's static-output cap (bytes). Internal API - do NOT call from
     * outside Tessera. Used by dispatch-time stamping on {@code GeneratorResult.StaticImage}.
     */
    public long staticOutputCapBytes() {
        return staticOutputCapBytes;
    }

    /**
     * Returns this engine's animated-output cap (bytes). Internal API - do NOT call from
     * outside Tessera. Used by dispatch-time stamping on {@code GeneratorResult.AnimatedImage}.
     */
    public long animatedOutputCapBytes() {
        return animatedOutputCapBytes;
    }

    // -------------------------------------------------------------------------
    // Internal dispatch (called by the fluent BuilderImpls)
    // -------------------------------------------------------------------------

    /**
     * Internal dispatch entry point called by the fluent builder impls. Pattern-matches
     * exhaustively over the sealed {@link CoreRenderRequest} hierarchy.
     *
     * @param request the internal request; must not be null
     * @param context the generation context; must not be null
     * @return the render result
     * @throws ClosedEngineException if this engine is closed
     * @throws RenderException if rendering fails
     * @throws ParseException if the request involves NBT parsing that fails
     */
    public GeneratorResult renderInternal(CoreRenderRequest request, GenerationContext context)
            throws RenderException, ParseException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        if (closed.get()) {
            throw new ClosedEngineException();
        }
        inflightRenders.incrementAndGet();
        try {
            GeneratorResult result = switch (request) {
                case ItemRequest r -> itemGenerator.render(r, context);
                case TooltipRequest r -> tooltipGenerator.render(r, context);
                case InventoryRequest r -> inventoryGenerator.render(r, context);
                case PlayerHeadRequest r -> playerHeadGenerator.render(r, context);
                case PlayerModelRequest r -> playerModelGenerator.render(r, context);
                case CompositeRequest r -> renderComposite(r, context);
            };
            return stampCaps(result);
        } finally {
            inflightRenders.decrementAndGet();
        }
    }

    /**
     * External-consumer plug-in dispatch. Looks up a {@code Generator} in the Class-keyed
     * fallback registry populated via the {@link net.aerh.tessera.spi.GeneratorFactory} SPI.
     *
     * @param request the external request; must not be null
     * @param context the generation context; must not be null
     * @return the render result
     * @throws ClosedEngineException if this engine is closed
     * @throws RenderException if rendering fails
     * @throws ValidationException if no Generator is registered for the request type
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public GeneratorResult renderExternal(RenderRequest request, GenerationContext context)
            throws RenderException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        if (closed.get()) {
            throw new ClosedEngineException();
        }
        inflightRenders.incrementAndGet();
        try {
            Generator g = externalRegistry.get(request.getClass());
            if (g == null) {
                throw new ValidationException("No registered Generator for " + request.getClass().getName(),
                        Map.of("requestType", request.getClass().getName()));
            }
            return stampCaps((GeneratorResult) g.render(request, context));
        } finally {
            inflightRenders.decrementAndGet();
        }
    }

    /**
     * Replaces the generator-emitted {@code GeneratorResult} with a copy carrying the
     * engine-resolved cap values. Generators construct {@code StaticImage} / {@code AnimatedImage}
     * via the no-cap constructors (cap = 0 signals "fall through to env/default at encode
     * time"); dispatch here stamps the builder-resolved caps so the engine override wins
     * over env / default.
     */
    private GeneratorResult stampCaps(GeneratorResult result) {
        return switch (result) {
            case GeneratorResult.StaticImage s -> s.staticCapBytes() == staticOutputCapBytes
                    ? s
                    : new GeneratorResult.StaticImage(s.image(), staticOutputCapBytes);
            case GeneratorResult.AnimatedImage a -> a.animatedCapBytes() == animatedOutputCapBytes
                    ? a
                    : new GeneratorResult.AnimatedImage(a.frames(), a.frameDelayMs(), animatedOutputCapBytes);
        };
    }

    @Override
    public ParsedItem parseNbt(String nbt) throws ParseException {
        Objects.requireNonNull(nbt, "nbt must not be null");
        return nbtParser.parse(nbt);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> DataRegistry<T> registry(RegistryKey<T> key) {
        Objects.requireNonNull(key, "key must not be null");
        DataRegistry<?> registry = registries.get(key.name());
        if (registry == null) {
            throw new RegistryException("No registry registered for key: " + key.name(),
                    Map.of("registryKey", key.name()));
        }
        return (DataRegistry<T>) registry;
    }

    @Override
    public SpriteProvider sprites() {
        return spriteProvider;
    }

    @Override
    public OverlayColorProvider overlayColors() {
        return overlayColorProvider;
    }

    @Override
    public Capabilities capabilities() {
        return capabilities;
    }

    /**
     * Six-step close sequence. Double-close is a no-op (CAS idempotency). Post-close render
     * calls throw {@link ClosedEngineException}.
     *
     * <ol>
     *   <li>Reject new render calls (set {@link #closed} flag - already done via
     *       {@link AtomicBoolean#compareAndSet(boolean, boolean)}).</li>
     *   <li>Drain in-flight renders up to {@link #shutdownTimeout}.</li>
     *   <li>Flush Caffeine caches (per-generator {@link CachingGenerator}).</li>
     *   <li>Close {@link net.aerh.tessera.api.resource.ResourcePack} handles in reverse order
     *       (innermost-first); a misbehaving close() is logged and swallowed so one bad pack
     *       cannot block shutdown.</li>
     *   <li>Close the shared {@link HttpClient}. Exceptions are logged + swallowed so one
     *       misbehaving client doesn't prevent executor shutdown.</li>
     *   <li>Shut down the per-engine executor (only when {@link #ownExecutor} is true).</li>
     * </ol>
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;  // Double-close is a no-op.
        }

        // Step 2: drain in-flight up to shutdownTimeout.
        long deadline = System.nanoTime() + shutdownTimeout.toNanos();
        while (inflightRenders.get() > 0 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(5_000_000L);  // 5ms
        }
        if (inflightRenders.get() > 0) {
            log.warn("close(): {} in-flight render(s) still running after {} drain timeout; "
                    + "proceeding with shutdown", inflightRenders.get(), shutdownTimeout);
        }

        // Step 3: flush Caffeine caches.
        invalidateIfCaching(itemGenerator);
        invalidateIfCaching(tooltipGenerator);
        invalidateIfCaching(inventoryGenerator);
        invalidateIfCaching(playerHeadGenerator);
        invalidateIfCaching(playerModelGenerator);

        // Step 4: close ResourcePack handles in reverse order (innermost-first).
        // A misbehaving close() MUST NOT prevent shutdown - log + continue.
        for (int i = resourcePacks.size() - 1; i >= 0; i--) {
            ResourcePack pack = resourcePacks.get(i);
            try {
                pack.close();
            } catch (Exception e) {
                log.warn("ResourcePack close failed: {}", pack, e);
            }
        }

        // Step 5: close the shared HttpClient.
        // HttpClient implements AutoCloseable since JDK 21.0.x; close() blocks until
        // in-flight requests terminate or times out after 3 minutes (JDK default).
        // Honour the user-configured shutdownTimeout so close() does not block for
        // 3 minutes on flaky Mojang calls when the engine advertises a shorter drain
        // window. Dispatch the blocking close to a throwaway daemon thread and
        // orTimeout the join; on timeout we log + swallow (HttpClient will finish
        // cleaning up its own workers in the background).
        try {
            CompletableFuture
                    .runAsync(() -> {
                        try {
                            httpClient.close();
                        } catch (Exception e) {
                            log.warn("HttpClient close failed", e);
                        }
                    }, r -> {
                        Thread t = new Thread(r, "tessera-httpclient-close");
                        t.setDaemon(true);
                        t.start();
                    })
                    .orTimeout(shutdownTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof java.util.concurrent.TimeoutException) {
                log.warn("HttpClient close did not complete within {}; abandoning wait", shutdownTimeout);
            } else {
                log.warn("HttpClient close failed", cause);
            }
        } catch (Exception e) {
            log.warn("HttpClient close failed", e);
        }

        // Step 6: shut down per-engine executor (only if we own it).
        if (ownExecutor) {
            if (executor instanceof BoundedVirtualExecutor bve) {
                try {
                    bve.awaitTermination(shutdownTimeout);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("awaitTermination interrupted during close()", e);
                }
                bve.close();
            } else if (executor instanceof AutoCloseable ac) {
                try {
                    ac.close();
                } catch (Exception e) {
                    log.warn("Executor close failed", e);
                }
            }
            // else: consumer-supplied or non-closeable executor; nothing to do.
        }
    }

    private static void invalidateIfCaching(Generator<?, ?> g) {
        if (g instanceof CachingGenerator<?, ?> cg) {
            try {
                cg.invalidate();
            } catch (Throwable t) {
                log.warn("CachingGenerator invalidate failed during close()", t);
            }
        }
    }

    /**
     * Fan-out composite rendering using {@link CompletableFuture#allOf} on the per-engine
     * executor. On sibling failure, remaining sub-renders are signalled via {@code cancel(false)}
     * but NOT interrupted ({@code CompletableFuture#cancel}'s {@code mayInterruptIfRunning} flag
     * is effectively ignored). Siblings run to completion; their results are discarded. This is
     * documented acceptable slack for 1.0.0 (typical per-render latency is under 200ms).
     */
    private GeneratorResult renderComposite(CompositeRequest request, GenerationContext context)
            throws RenderException {
        List<RenderRequest> subs = request.requests();
        if (subs.isEmpty()) {
            return ResultComposer.compose(List.of(), request.layout(), request.padding(),
                    request.gridRows(), request.gridCols());
        }

        List<CompletableFuture<GeneratorResult>> futures = new ArrayList<>(subs.size());
        for (RenderRequest sub : subs) {
            RenderRequest effective = sub.withInheritedScale(request.scaleFactor());
            CompletableFuture<GeneratorResult> f = CompletableFuture.supplyAsync(() -> {
                try {
                    return effective instanceof CoreRenderRequest core
                            ? renderInternal(core, context)
                            : renderExternal(effective, context);
                } catch (RenderException | ParseException e) {
                    throw new CompletionException(e);
                }
            }, executor);
            futures.add(f);
        }

        // Build the allOf exactly once; register the sibling-cancellation side-effect on the
        // same CompletableFuture we later join against. The exceptionally() chain is retained
        // only for its side-effect (cancel(false) on pending siblings on first failure,
        // non-interrupting); the returned stage is intentionally unused beyond that
        // suppressing the discard warning via an explicit local so a future maintainer
        // doesn't mistake it for dead code.
        CompletableFuture<?>[] futArr = futures.toArray(new CompletableFuture[0]);
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futArr);
        @SuppressWarnings("unused")
        CompletableFuture<Void> siblingCancellationSignal = allDone.exceptionally(ex -> {
            for (CompletableFuture<GeneratorResult> sibling : futures) {
                sibling.cancel(false);
            }
            return null;
        });

        try {
            allDone.join();
            List<GeneratorResult> results = new ArrayList<>(futures.size());
            for (CompletableFuture<GeneratorResult> f : futures) {
                results.add(f.join());
            }
            return ResultComposer.compose(results, request.layout(), request.padding(),
                    request.gridRows(), request.gridCols());
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RenderException re) throw re;
            if (cause instanceof ParseException pe) {
                throw new RenderException("composite sub-request parse failed",
                        Map.of(), pe);
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new RenderException("Composite render interrupted", Map.of(), cause);
            }
            throw new RenderException("Composite sub-request failed",
                    Map.of("cause", String.valueOf(cause.getMessage())), cause);
        }
    }
}
