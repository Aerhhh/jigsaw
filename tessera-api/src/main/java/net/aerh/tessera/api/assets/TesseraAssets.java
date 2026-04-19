package net.aerh.tessera.api.assets;

import net.aerh.tessera.api.exception.TesseraAssetDownloadException;
import net.aerh.tessera.api.exception.TesseraAssetIntegrityException;
import net.aerh.tessera.api.exception.TesseraEulaNotAcceptedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

/**
 * Explicit consumer-side bootstrap for the Minecraft assets Tessera needs to render.
 *
 * <p>Must be called once before {@link net.aerh.tessera.api.Engine#builder()} is used. A typical
 * flow:
 *
 * <pre>{@code
 * TesseraAssets.fetch("1.21.4");
 * Engine engine = Engine.builder()
 *     .minecraftVersion("1.21.4")
 *     .acceptMojangEula(true)
 *     .build();
 * }</pre>
 *
 * <p>The Mojang EULA must have been accepted via one of:
 * <ul>
 *   <li>{@link net.aerh.tessera.api.EngineBuilder#acceptMojangEula(boolean)}</li>
 *   <li>environment variable {@code TESSERA_ACCEPT_MOJANG_EULA=true}</li>
 *   <li>system property {@code -Dtessera.accept.mojang.eula=true}</li>
 * </ul>
 *
 * @see net.aerh.tessera.api.EngineBuilder#acceptMojangEula(boolean)
 */
public final class TesseraAssets {

    private static final Logger log = LoggerFactory.getLogger(TesseraAssets.class);

    // Constrain mcVer to Minecraft-version-shaped strings to prevent path traversal.
    private static final Pattern MC_VER_PATTERN =
            Pattern.compile("^[0-9]+\\.[0-9]+(\\.[0-9]+)?(-[a-z0-9]+)?$");

    private TesseraAssets() { /* static-only */ }

    /**
     * Fetches all assets named in the classpath manifest for {@code mcVer} into the default
     * cache directory. Idempotent: files already present with matching SHA1 are skipped.
     *
     * @param mcVer the Minecraft version (e.g. {@code "1.21.4"}); must match
     *              {@code ^[0-9]+\.[0-9]+(\.[0-9]+)?(-[a-z0-9]+)?$}
     * @throws TesseraEulaNotAcceptedException if the Mojang EULA has not been accepted
     * @throws TesseraAssetDownloadException if an HTTP download fails
     * @throws TesseraAssetIntegrityException if SHA1 verification fails
     * @throws IllegalArgumentException if {@code mcVer} does not match the allowed format
     * @throws NullPointerException if {@code mcVer} is {@code null}
     */
    public static void fetch(String mcVer)
            throws TesseraAssetDownloadException, TesseraAssetIntegrityException {
        fetch(mcVer, null, false);
    }

    /**
     * Overload honouring builder-level EULA acceptance plus an optional cache-dir override.
     *
     * <p>Public because {@link net.aerh.tessera.api.EngineBuilder} implementations live under
     * {@code core} and need a way to pass builder state in. Callers should prefer
     * {@link #fetch(String)}.
     *
     * @param mcVer the Minecraft version; must not be {@code null}
     * @param cacheDirOverride optional cache-dir override ({@code null} = default resolution)
     * @param builderAcceptedEula whether {@code EngineBuilder.acceptMojangEula(true)} was called
     * @throws TesseraEulaNotAcceptedException if the Mojang EULA has not been accepted
     * @throws TesseraAssetDownloadException if an HTTP download fails
     * @throws TesseraAssetIntegrityException if SHA1 verification fails
     * @throws IllegalArgumentException if {@code mcVer} does not match the allowed format
     * @throws NullPointerException if {@code mcVer} is {@code null}
     */
    public static void fetch(String mcVer, Path cacheDirOverride, boolean builderAcceptedEula)
            throws TesseraAssetDownloadException, TesseraAssetIntegrityException {
        fetch(mcVer, cacheDirOverride, builderAcceptedEula, new DownloadPipeline());
    }

    /**
     * Test-injection overload. Package-private so the idempotence test (which lives in this same
     * package under {@code src/test/java}) can pass a stub {@link DownloadPipeline}. Mirrors the
     * {@code DownloadPipeline(HttpClient)} package-private seam used for cross-seam injection.
     *
     * @param mcVer the Minecraft version
     * @param cacheDirOverride optional cache dir override
     * @param builderAcceptedEula builder-level EULA flag
     * @param pipeline the download pipeline to route per-entry downloads through
     */
    static void fetch(String mcVer, Path cacheDirOverride, boolean builderAcceptedEula,
                      DownloadPipeline pipeline)
            throws TesseraAssetDownloadException, TesseraAssetIntegrityException {
        Objects.requireNonNull(mcVer, "mcVer must not be null");
        if (!MC_VER_PATTERN.matcher(mcVer).matches()) {
            throw new IllegalArgumentException(
                    "mcVer must match " + MC_VER_PATTERN.pattern() + ", got: " + mcVer);
        }
        Objects.requireNonNull(pipeline, "pipeline must not be null");

        EulaGate.requireEulaAcceptance(builderAcceptedEula);

        AssetManifest manifest = ManifestLoader.load(mcVer);
        Path cacheDir = CacheLocator.resolve(cacheDirOverride, mcVer);

        log.info("Tessera: fetching {} assets for MC {} into {}",
                manifest.files().size(), mcVer, cacheDir);
        for (AssetEntry entry : manifest.files()) {
            pipeline.download(entry, cacheDir);
        }

        // Give each registered AssetProvider a chance to hydrate version-specific bytes
        // that live outside piston-meta (post-1.19 item textures are bundled inside
        // client.jar). The default AssetProvider.hydrate is a no-op, so older providers
        // are unaffected.
        HttpClient httpClient = pipeline.httpClient();
        for (AssetProvider provider : ServiceLoader.load(AssetProvider.class)) {
            if (!provider.supportedVersions().contains(mcVer)) {
                continue;
            }
            try {
                provider.hydrate(httpClient);
            } catch (IOException e) {
                throw new TesseraAssetDownloadException(
                        "hydrate() failed for mcVer=" + mcVer + " provider="
                                + provider.getClass().getName(),
                        java.util.Map.of("mcVer", mcVer, "provider", provider.getClass().getName()),
                        e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TesseraAssetDownloadException(
                        "hydrate() interrupted for mcVer=" + mcVer + " provider="
                                + provider.getClass().getName(),
                        java.util.Map.of("mcVer", mcVer, "provider", provider.getClass().getName()),
                        e);
            }
        }

        log.info("Tessera: fetch complete for MC {}", mcVer);
    }
}
