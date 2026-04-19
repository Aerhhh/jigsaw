package net.aerh.tessera.api.assets;

import net.aerh.tessera.api.exception.TesseraAssetIntegrityException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Set;

/**
 * Contract for supplying per-Minecraft-version asset data. Implementations are
 * discovered via {@link java.util.ServiceLoader} (auto, via
 * {@code META-INF/services/net.aerh.tessera.api.assets.AssetProvider}) and/or
 * registered programmatically via
 * {@link net.aerh.tessera.api.EngineBuilder#assetProvider(AssetProvider)}
 * (programmatic wins on version collision).
 *
 * <p>This contract lives in {@code tessera-api.assets}, NOT {@code tessera-spi}. The
 * ServiceLoader discovery + programmatic registration flow is unchanged; only the
 * Maven module is different. The relocation avoids an {@code api -> spi} compile-time
 * dependency that would violate the api-module boundary.
 *
 * <p>Version matching is strict exact-string. Providers self-describe
 * their supported version(s) via {@link #supportedVersions()}; Tessera never parses the
 * version string.
 */
public interface AssetProvider {

    /**
     * The set of Minecraft version strings this provider supports. Matched by EXACT
     * STRING against {@code Engine.builder().minecraftVersion(String)}. No wildcards,
     * no normalization.
     *
     * @return a non-null, non-empty set of version identifiers (e.g. {@code {"26.1.2"}})
     */
    Set<String> supportedVersions();

    /**
     * Resolves the absolute cache root path for the given version — the directory
     * {@code AtlasSpriteProvider}, {@code ResourceFontProvider}, overlay loaders and
     * equipment-JSON loaders read from at runtime.
     *
     * @param version a version string from {@link #supportedVersions()}
     * @return the absolute path to the version's on-disk asset root
     */
    Path resolveAssetRoot(String version);

    /**
     * Advisory capability flags. Does NOT participate in resolution;
     * consumers call {@link net.aerh.tessera.api.Engine#capabilities()} to introspect.
     *
     * @return an immutable {@link Capabilities} record populated at provider load time
     */
    Capabilities capabilities();

    /**
     * Optional hydration hook invoked by {@link TesseraAssets#fetch(String)} after the
     * piston-meta download loop, giving each provider a chance to fetch and extract
     * version-specific bytes that do NOT appear in piston-meta's {@code objects} list
     * (post-1.19 Mojang bundles item/block textures and models inside {@code client.jar},
     * not as individually-downloadable objects).
     *
     * <p>The default implementation is a no-op, preserving backward compatibility with
     * providers that only rely on piston-meta entries.
     *
     * <p>Called <em>after</em> {@link TesseraAssets#fetch(String)} has verified the Mojang
     * EULA gate and downloaded every {@link AssetEntry} - so implementations may assume the
     * cache directory exists and standard piston-meta files are already present.
     *
     * @param httpClient the HTTP client to route downloads through (shares the
     *                   {@link DownloadPipeline}'s virtual-thread executor + proxy config)
     * @throws TesseraAssetIntegrityException if any integrity check fails (e.g. client.jar
     *                                        SHA-1 mismatch, or zip-slip in extracted entries)
     * @throws IOException if the disk or network IO fails for non-integrity
     *                                        reasons
     * @throws InterruptedException if the HTTP send is interrupted
     */
    default void hydrate(HttpClient httpClient)
            throws TesseraAssetIntegrityException, IOException, InterruptedException {
        /* default no-op - providers that only rely on piston-meta entries need no extra hydration */
    }
}
