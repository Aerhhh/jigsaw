package net.aerh.tessera.api.assets;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves the on-disk asset cache directory per CONTEXT.md.
 *
 * <p>Precedence: builder override &gt; {@code TESSERA_ASSET_DIR} env var &gt; platform default.
 *
 * <p>Platform defaults:
 * <ul>
 *   <li>Linux: {@code $XDG_CACHE_HOME/tessera} or {@code ~/.cache/tessera}</li>
 *   <li>macOS: {@code ~/Library/Caches/Tessera}</li>
 *   <li>Windows: {@code %LOCALAPPDATA%\Tessera} (fallback {@code %USERPROFILE%\AppData\Local\Tessera})</li>
 * </ul>
 */
public final class CacheLocator {

    private CacheLocator() { /* static-only */ }

    /**
     * Resolves the cache directory for the given Minecraft version.
     *
     * @param builderOverride explicit builder-supplied cache root; may be {@code null}
     * @param mcVer the Minecraft version (e.g. {@code "1.21.4"})
     * @return the resolved cache directory (not created here)
     * @throws NullPointerException if {@code mcVer} is null
     */
    public static Path resolve(Path builderOverride, String mcVer) {
        Objects.requireNonNull(mcVer, "mcVer must not be null");
        if (builderOverride != null) {
            return builderOverride.resolve("assets").resolve(mcVer);
        }
        String envOverride = System.getenv("TESSERA_ASSET_DIR");
        if (envOverride != null && !envOverride.isBlank()) {
            return Path.of(envOverride).resolve("assets").resolve(mcVer);
        }
        return defaultFor(System.getProperty("os.name", "")).resolve("assets").resolve(mcVer);
    }

    /**
     * Returns the platform-default Tessera cache base directory (WITHOUT the per-version suffix).
     * Package-private for direct unit testing.
     *
     * @param osName the {@code os.name} system property
     * @return the platform-default cache base directory
     */
    static Path defaultFor(String osName) {
        String home = System.getProperty("user.home");
        if (osName.startsWith("Windows")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(home, "AppData", "Local");
            return base.resolve("Tessera");
        }
        if (osName.startsWith("Mac")) {
            return Path.of(home, "Library", "Caches", "Tessera");
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = (xdg != null && !xdg.isBlank())
                ? Path.of(xdg)
                : Path.of(home, ".cache");
        return base.resolve("tessera");
    }
}
