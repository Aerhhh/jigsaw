package net.aerh.tessera.assets.v26_1_2;

import net.aerh.tessera.api.assets.AssetManifest;
import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.assets.CacheLocator;
import net.aerh.tessera.api.assets.Capabilities;
import net.aerh.tessera.api.assets.ClientJarSection;
import net.aerh.tessera.api.assets.ManifestLoader;
import net.aerh.tessera.api.exception.TesseraAssetIntegrityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * {@link AssetProvider} impl for Minecraft 26.1.2. Registered via
 * {@code META-INF/services/net.aerh.tessera.api.assets.AssetProvider} so {@code
 * AssetProviderResolver} discovers it at engine-build time.
 *
 * <p>The manifest at {@code src/main/resources/tessera/assets/26.1.2/manifest.json} is
 * regenerated from piston-meta. Consumers on Tessera 1.0.0 pull this artifact
 * via {@code com.github.Aerhhh.tessera:tessera-assets-26.1.2:<ver>}.
 *
 * <p>The {@code resolveAssetRoot} implementation reuses {@link CacheLocator}'s
 * XDG-compliant cross-platform resolution. There is deliberately no per-engine
 * builder override at this layer: the resolver call site has no access to the
 * builder's {@code assetDir(Path)} override, so the override is applied in
 * {@code DefaultEngine.Builder.verifyAssetsPresent()} via
 * {@code CacheLocator.resolve(builderOverride, mcVer)} directly. If the override path
 * ever needs to thread through the resolver itself, this is the seam to extend.
 */
public final class Assets_26_1_2_Provider implements AssetProvider {

    private static final Logger log = LoggerFactory.getLogger(Assets_26_1_2_Provider.class);

    /** The single MC version this provider serves. */
    public static final String MC_VERSION = "26.1.2";

    @Override
    public Set<String> supportedVersions() {
        return Set.of(MC_VERSION);
    }

    @Override
    public Path resolveAssetRoot(String version) {
        Objects.requireNonNull(version, "version must not be null");
        if (!MC_VERSION.equals(version)) {
            throw new IllegalArgumentException(
                    "Assets_26_1_2_Provider only supports " + MC_VERSION + ", got: " + version);
        }
        return CacheLocator.resolve(null, MC_VERSION);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(
                true,         // hasComponents: 26.x has full data-components support
                true,         // hasFlattening: 26.x is post-flattening (1.13+)
                true,         // supportsAnimatedInventory
                MC_VERSION);
    }

    /**
     * Hydration: loads the bundled 26.1.2 manifest, and if it carries a
     * {@code client_jar} section, invokes {@link ClientJarExtractor#extract} to fetch and
     * unpack the Mojang-bundled texture/model/font roots into the on-disk cache. Idempotent:
     * if the cache already has {@code assets/minecraft/textures/item/} populated, the
     * extraction is skipped.
     */
    @Override
    public void hydrate(HttpClient httpClient)
            throws TesseraAssetIntegrityException, IOException, InterruptedException {
        Objects.requireNonNull(httpClient, "httpClient must not be null");

        AssetManifest manifest = ManifestLoader.load(MC_VERSION);
        ClientJarSection clientJar = manifest.clientJar();
        if (clientJar == null) {
            log.debug("26.1.2 manifest has no client_jar section; hydrate() is a no-op");
            return;
        }

        Path cacheRoot = resolveAssetRoot(MC_VERSION);
        // Idempotency marker keyed on the client.jar sha1. A partial extraction
        // (e.g. crash mid-loop) leaves no marker, so the next hydrate call re-runs the
        // full extract. A version bump changes the sha1 and auto-invalidates the marker.
        Path marker = cacheRoot.resolve("tessera/extraction-complete.sha1");
        if (Files.exists(marker)) {
            String recorded = Files.readString(marker).trim();
            if (recorded.equalsIgnoreCase(clientJar.sha1())) {
                log.debug("Skipping client.jar extraction; marker matches {}", recorded);
                return;
            }
            log.info("Extraction marker sha1 {} differs from client.jar sha1 {}; re-extracting",
                    recorded, clientJar.sha1());
        }

        log.info("Hydrating 26.1.2 cache: extracting client.jar from {}", clientJar.url());
        ClientJarExtractor.extract(URI.create(clientJar.url()),
                clientJar.sha1(), cacheRoot, httpClient);

        // Record the marker only after the extractor returned without throwing.
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, clientJar.sha1());
    }
}
