package net.aerh.tessera.api;

import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.data.RegistryKey;
import net.aerh.tessera.api.effect.ImageEffect;
import net.aerh.tessera.api.exception.TesseraAssetsMissingException;
import net.aerh.tessera.api.font.FontProvider;
import net.aerh.tessera.api.overlay.OverlayRenderer;
import net.aerh.tessera.api.resource.ResourcePack;
import net.aerh.tessera.api.sprite.SpriteProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * A builder that constructs a fully configured {@link Engine} instance.
 *
 * <p>By default, the engine is built with all defaults enabled (sprite provider,
 * data registries, built-in effects, font registry, overlay registry). Call
 * {@link #noDefaults()} to opt out of defaults and configure everything manually.
 *
 * @see Engine
 */
public interface EngineBuilder {

    /**
     * Disables all defaults so only the components registered via this builder are used.
     *
     * @return this builder
     */
    EngineBuilder noDefaults();

    /**
     * Adds an additional {@link ImageEffect} to the effect pipeline.
     *
     * @param effect the effect to add; must not be {@code null}
     * @return this builder
     */
    EngineBuilder effect(ImageEffect effect);

    /**
     * Registers an additional {@link OverlayRenderer}.
     *
     * @param renderer the renderer to register; must not be {@code null}
     * @return this builder
     */
    EngineBuilder overlayRenderer(OverlayRenderer renderer);

    /**
     * Registers an additional {@link FontProvider}.
     *
     * @param provider the font provider to register; must not be {@code null}
     * @return this builder
     */
    EngineBuilder fontProvider(FontProvider provider);

    /**
     * Sets a custom {@link SpriteProvider} to use for loading item and block textures.
     * If not called, the engine defaults to the built-in texture atlas.
     *
     * @param provider the sprite provider; must not be {@code null}
     * @return this builder
     */
    EngineBuilder spriteProvider(SpriteProvider provider);

    /**
     * Registers a {@link DataRegistry} that will be available via {@link Engine#registry(RegistryKey)}.
     * If a registry with the same key name is already registered, the new one replaces it.
     *
     * @param <T>      the type of objects stored in the registry
     * @param registry the data registry to register; must not be {@code null}
     * @return this builder
     */
    <T> EngineBuilder registry(DataRegistry<T> registry);

    /**
     * Sets a custom slot texture for inventory rendering.
     * If not called, the engine uses the bundled default slot texture.
     *
     * @param slotTexture the slot texture image; must not be {@code null}
     * @return this builder
     */
    EngineBuilder slotTexture(java.awt.image.BufferedImage slotTexture);

    /**
     * Sets a {@link ResourcePack} to use for loading armor textures by material name.
     *
     * <p>If not called, armor can still be provided via pre-loaded armor piece images on the
     * fluent player-model builder.
     *
     * @param resourcePack the resource pack; must not be {@code null}
     * @return this builder
     */
    EngineBuilder resourcePack(ResourcePack resourcePack);

    /**
     * Accepts the Mojang EULA programmatically. Equivalent to setting the
     * {@code TESSERA_ACCEPT_MOJANG_EULA=true} environment variable or the
     * {@code -Dtessera.accept.mojang.eula=true} system property.
     *
     * <p>Required for {@link net.aerh.tessera.api.assets.TesseraAssets#fetch(String)} unless one
     * of the env-var / system-property fallbacks is set.
     *
     * @param accept {@code true} to accept the Mojang EULA
     * @return this builder
     * @see net.aerh.tessera.api.assets.TesseraAssets
     */
    EngineBuilder acceptMojangEula(boolean accept);

    /**
     * Overrides the Minecraft version whose assets Tessera will load from cache.
     *
     * <p>When set, {@link #build()} verifies every entry in the pinned manifest for {@code mcVer}
     * is present in the resolved cache directory; if any are missing, it throws
     * {@link TesseraAssetsMissingException} pointing at
     * {@link net.aerh.tessera.api.assets.TesseraAssets#fetch(String)}.
     *
     * @param mcVer the Minecraft version (e.g. {@code "1.21.4"}); must match
     *              {@code ^[0-9]+\.[0-9]+(\.[0-9]+)?(-[a-z0-9]+)?$}
     * @return this builder
     * @throws NullPointerException if {@code mcVer} is {@code null}
     */
    EngineBuilder minecraftVersion(String mcVer);

    /**
     * Overrides the asset cache root directory. Precedence:
     * this &gt; {@code TESSERA_ASSET_DIR} env var &gt; platform default.
     *
     * @param dir the cache root; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code dir} is {@code null}
     */
    EngineBuilder assetDir(Path dir);

    /**
     * Programmatically registers an {@link AssetProvider} for a specific Minecraft version per
     *  + . Programmatic registration wins over {@link java.util.ServiceLoader}
     * auto-discovery on version collision (last-wins; a WARN log naming both classes is emitted
     * ).
     *
     * @param provider the asset provider; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code provider} is {@code null}
     */
    EngineBuilder assetProvider(AssetProvider provider);

    /**
     * Overrides the bound on concurrent render tasks. Default is
     * {@code max(16, Runtime.getRuntime().availableProcessors() * 4)}. Env fallback:
     * {@code TESSERA_EXECUTOR_BOUND}. This method takes precedence over the env variable.
     *
     * <p>If {@link #executor(Executor)} is also called on this builder instance, the
     * {@code executor(Executor)} call wins and a WARN is logged.
     *
     * @param bound the concurrency bound; must be {@code >= 1}
     * @return this builder
     * @throws IllegalArgumentException if {@code bound < 1}
     */
    EngineBuilder executorBound(int bound);

    /**
     * Overrides the engine's executor with a caller-supplied one. When set, the
     * engine does NOT own the executor; {@link Engine#close()} will NOT shut it down
     * (consumer owns its lifecycle). Wins over {@link #executorBound(int)}.
     *
     * @param executor the executor; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code executor} is {@code null}
     */
    EngineBuilder executor(Executor executor);

    /**
     * Overrides the engine's shutdown drain-timeout. Default is 5 seconds. Applied
     * by {@link Engine#close()} when draining in-flight renders.
     *
     * @param timeout the shutdown timeout; must not be {@code null} or negative
     * @return this builder
     * @throws NullPointerException if {@code timeout} is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is negative
     */
    EngineBuilder shutdownTimeout(Duration timeout);

    /**
     * Overrides the static-image output size cap   / . Applied at the
     * encoder boundary ({@code GeneratorResult.StaticImage.toBytes()}); encoded outputs that
     * exceed this cap fail fast with
     * {@link net.aerh.tessera.api.exception.OutputTooLargeException}.
     *
     * <p>Default is 8 MB (Discord free-tier attachment ceiling). Env fallback:
     * {@code TESSERA_STATIC_OUTPUT_CAP_BYTES}. Builder override wins.
     *
     * @param bytes the cap in bytes; must be {@code > 0}
     * @return this builder
     * @throws IllegalArgumentException if {@code bytes <= 0}
     */
    EngineBuilder staticOutputCapBytes(long bytes);

    /**
     * Overrides the animated-output size cap   / . Applied at the
     * encoder boundary ({@code GeneratorResult.AnimatedImage.toWebpBytes()} /
     * {@code toGifBytes()}); encoded outputs that exceed this cap fail fast with
     * {@link net.aerh.tessera.api.exception.OutputTooLargeException}.
     *
     * <p>Default is 24 MB. Env fallback: {@code TESSERA_ANIMATED_OUTPUT_CAP_BYTES}. Builder
     * override wins.
     *
     * @param bytes the cap in bytes; must be {@code > 0}
     * @return this builder
     * @throws IllegalArgumentException if {@code bytes <= 0}
     */
    EngineBuilder animatedOutputCapBytes(long bytes);

    /**
     * Builds and returns the configured {@link Engine}.
     *
     * @return a new {@link Engine} instance
     * @throws TesseraAssetsMissingException if {@link #minecraftVersion(String)} was set and the
     *         required assets are not present in the resolved cache directory - remediation: call
     *         {@link net.aerh.tessera.api.assets.TesseraAssets#fetch(String)} first.
     */
    Engine build() throws TesseraAssetsMissingException;
}
