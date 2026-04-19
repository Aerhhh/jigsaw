package net.aerh.tessera.api;

import net.aerh.tessera.api.assets.Capabilities;
import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.data.RegistryKey;
import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.generator.CompositeBuilder;
import net.aerh.tessera.api.generator.FromNbtBuilder;
import net.aerh.tessera.api.generator.InventoryBuilder;
import net.aerh.tessera.api.generator.ItemBuilder;
import net.aerh.tessera.api.generator.PlayerHeadBuilder;
import net.aerh.tessera.api.generator.PlayerModelBuilder;
import net.aerh.tessera.api.generator.TooltipBuilder;
import net.aerh.tessera.api.nbt.ParsedItem;
import net.aerh.tessera.api.overlay.OverlayColorProvider;
import net.aerh.tessera.api.sprite.SpriteProvider;

import java.util.ServiceLoader;

/**
 * Entry point for the Tessera rendering engine.
 *
 * <p>Obtain an instance via {@link #builder()}, then use the fluent entry points
 * ({@link #item()}, {@link #tooltip()}, {@link #composite()}...) to construct and render:
 *
 * <pre>{@code
 * try (Engine engine = Engine.builder()
 *         .minecraftVersion("26.1.2")
 *         .acceptMojangEula(true)
 *         .build()) {
 *     GeneratorResult result = engine.item()
 *             .itemId("diamond_sword")
 *             .enchanted(true)
 *             .render();
 * }
 * }</pre>
 *
 * <p>{@code Engine} is an {@link AutoCloseable}; closing an engine drains in-flight renders,
 * flushes caches, closes resource-pack handles, and shuts down the per-engine executor.
 *
 * @see EngineBuilder
 */
public interface Engine extends AutoCloseable {

    /**
     * Starts a fluent {@link ItemBuilder} terminal-chain.
     *
     * @return a new item builder
     */
    ItemBuilder item();

    /**
     * Starts a fluent {@link TooltipBuilder} terminal-chain.
     *
     * @return a new tooltip builder
     */
    TooltipBuilder tooltip();

    /**
     * Starts a fluent {@link InventoryBuilder} terminal-chain.
     *
     * @return a new inventory builder
     */
    InventoryBuilder inventory();

    /**
     * Starts a fluent {@link PlayerHeadBuilder} terminal-chain.
     *
     * @return a new player-head builder
     */
    PlayerHeadBuilder playerHead();

    /**
     * Starts a fluent {@link PlayerModelBuilder} terminal-chain.
     *
     * @return a new player-model builder
     */
    PlayerModelBuilder playerModel();

    /**
     * Starts a fluent {@link CompositeBuilder} terminal-chain.
     *
     * @return a new composite builder
     */
    CompositeBuilder composite();

    /**
     * Starts a fluent {@link FromNbtBuilder} terminal-chain with the given NBT payload.
     *
     * @param nbt the raw NBT string (SNBT); must not be {@code null}
     * @return a new NBT-sourced builder
     */
    FromNbtBuilder fromNbt(String nbt);

    /**
     * Parses the given raw NBT string and returns the structured item data without rendering.
     *
     * @param nbt the raw NBT string
     * @return the parsed item
     * @throws ParseException if the string cannot be parsed
     */
    ParsedItem parseNbt(String nbt) throws ParseException;

    /**
     * Returns the data registry associated with the given key.
     *
     * @param key the registry key
     * @param <T> the type of objects stored in the registry
     * @return the data registry
     */
    <T> DataRegistry<T> registry(RegistryKey<T> key);

    /**
     * Returns the sprite provider used by this engine.
     *
     * @return the sprite provider
     */
    SpriteProvider sprites();

    /**
     * Returns the overlay color provider, which exposes named color options from all overlay
     * categories for use in autocomplete and color resolution.
     *
     * @return the {@link OverlayColorProvider}
     */
    OverlayColorProvider overlayColors();

    /**
     * Returns the advisory {@link Capabilities} describing the currently-resolved asset
     * provider. Populated at {@link EngineBuilder#build()} time and constant for the engine's
     * lifetime. Does NOT participate in resolution.
     *
     * @return a non-null capabilities record
     */
    Capabilities capabilities();

    /**
     * Closes this engine, releasing all owned resources (executor, resource-pack handles,
     * HTTP client, Caffeine caches). Close does the following in order:
     * <ol>
     *   <li>Rejects new render / renderAsync calls</li>
     *   <li>Drains in-flight renders up to the configured shutdown timeout</li>
     *   <li>Flushes Caffeine caches</li>
     *   <li>Closes resource-pack handles</li>
     *   <li>Closes the HTTP client used by asset / skin loaders</li>
     *   <li>Shuts the per-engine executor down (when not supplied by the consumer)</li>
     * </ol>
     *
     * <p>A second {@code close()} call is a no-op.
     */
    @Override
    void close();

    /**
     * Registers a JVM shutdown hook that invokes {@link #close()} on this engine.
     * Opt-in helper; not registered automatically at {@link EngineBuilder#build()}.
     *
     * @return this engine (for chaining)
     */
    default Engine registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "tessera-shutdown"));
        return this;
    }

    /**
     * Returns a new {@link EngineBuilder} resolved via {@link ServiceLoader}. The concrete
     * builder implementation is provided by {@code tessera-core} (or any other runtime
     * dependency that ships a {@code META-INF/services/net.aerh.tessera.api.EngineBuilder}
     * descriptor).
     *
     * @return a new builder
     * @throws IllegalStateException if no {@code EngineBuilder} implementation is on the
     *                               classpath (add {@code tessera-core} as a runtime dep)
     */
    static EngineBuilder builder() {
        return ServiceLoader.load(EngineBuilder.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No EngineBuilder on classpath. Add tessera-core as a runtime dependency."));
    }
}
