package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.assets.Capabilities;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Test-only {@link AssetProvider} registered via
 * {@code src/test/resources/META-INF/services/net.aerh.tessera.api.assets.AssetProvider}
 * so {@link AssetProviderResolutionTest#emptyBuilderListFallsBackToServiceLoader()} can
 * verify the {@link AssetProviderResolver} ServiceLoader fallback path without depending
 * on {@code tessera-assets-26.1.2} (which would create a circular test-scope dependency).
 *
 * <p>The version it claims is {@code "test-26.x"} - a deliberately fake string that no
 * real provider in the wild would ever serve, so production resolution is never affected
 * by this test fixture even if it leaks onto a runtime classpath.
 */
public final class TestAssetProvider implements AssetProvider {

    public static final String VERSION = "test-26.x";

    @Override
    public Set<String> supportedVersions() {
        return Set.of(VERSION);
    }

    @Override
    public Path resolveAssetRoot(String version) {
        return Paths.get("/tmp/tessera-test-cache").resolve(version);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true, VERSION);
    }
}
