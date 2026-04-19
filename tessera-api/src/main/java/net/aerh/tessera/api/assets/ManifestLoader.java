package net.aerh.tessera.api.assets;

import com.google.gson.Gson;
import net.aerh.tessera.api.assets.AssetManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads the pinned {@link AssetManifest} for a given Minecraft version from the classpath
 * resource {@code /tessera/assets/<mcVer>/manifest.json}.
 */
public final class ManifestLoader {

    private static final Logger log = LoggerFactory.getLogger(ManifestLoader.class);
    private static final Gson GSON = new Gson();
    private static final String RESOURCE_FORMAT = "/tessera/assets/%s/manifest.json";

    private ManifestLoader() { /* static-only */ }

    /**
     * Loads the manifest for the requested Minecraft version from the classpath.
     *
     * @param mcVer the Minecraft version (e.g. {@code "1.21.4"}); must not be {@code null}
     * @return the parsed manifest
     * @throws NullPointerException if {@code mcVer} is null
     * @throws IllegalStateException if the manifest resource is not on the classpath
     * @throws UncheckedIOException if reading the resource fails
     */
    public static AssetManifest load(String mcVer) {
        Objects.requireNonNull(mcVer, "mcVer must not be null");
        String path = String.format(RESOURCE_FORMAT, mcVer);
        try (InputStream in = ManifestLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Manifest not on classpath: " + path);
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                AssetManifest m = GSON.fromJson(r, AssetManifest.class);
                log.debug("Loaded manifest for {}: {} entries", mcVer, m.files().size());
                return m;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read manifest: " + path, e);
        }
    }

    /**
     * Returns {@code true} if a manifest resource exists on the classpath for {@code mcVer}.
     * Does not parse the resource.
     *
     * @param mcVer the Minecraft version; must not be {@code null}
     * @return whether the manifest resource is present on the classpath
     */
    public static boolean exists(String mcVer) {
        Objects.requireNonNull(mcVer, "mcVer must not be null");
        return ManifestLoader.class.getResource(String.format(RESOURCE_FORMAT, mcVer)) != null;
    }
}
