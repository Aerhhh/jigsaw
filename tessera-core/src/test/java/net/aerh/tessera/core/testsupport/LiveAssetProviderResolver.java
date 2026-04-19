package net.aerh.tessera.core.testsupport;

import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.core.engine.AssetProviderResolver;

import java.util.List;

/**
 * Test-only helper that resolves the live {@link AssetProvider} against the on-disk
 * asset cache.
 *
 * <p>Introduced to collapse the provider-resolution boilerplate that every re-enabled
 * {@code @Disabled("awaits atlas pipeline")} test needed. Call {@link #resolve26_1_2()}
 * from {@code @BeforeAll} in a test class that is already guarded by
 * {@code @EnabledIfEnvironmentVariable(TESSERA_ASSETS_AVAILABLE=true)} so the resolver
 * only fires when the on-disk cache has been hydrated.
 *
 * <p>Intentionally not a public API: this is test plumbing for tessera-core tests that
 * assert behaviour against the hydrated atlas + font + overlay data. Consumers should
 * use {@link net.aerh.tessera.api.Engine#builder()} instead.
 */
public final class LiveAssetProviderResolver {

    /** Minecraft version pinned for the current release. */
    public static final String MC_VER = "26.1.2";

    private LiveAssetProviderResolver() { /* static-only */ }

    /**
     * Resolves the {@link AssetProvider} registered for Minecraft {@value #MC_VER} via the
     * {@link java.util.ServiceLoader} pathway.
     *
     * @return the resolved provider (never {@code null})
     * @throws net.aerh.tessera.api.exception.UnsupportedMinecraftVersionException
     *         if no provider is registered for {@value #MC_VER} (typically a classpath
     *         misconfiguration since {@code tessera-assets-26.1.2} is declared as a
     *         test-scope dep on tessera-core)
     */
    public static AssetProvider resolve26_1_2() {
        return AssetProviderResolver.resolve(MC_VER, List.of(), "test");
    }
}
