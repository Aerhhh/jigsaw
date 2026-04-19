package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.assets.Capabilities;
import net.aerh.tessera.api.exception.UnsupportedMinecraftVersionException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the.. contract on {@link AssetProviderResolver}:
 * <ul>
 *   <li>Programmatic registration resolves by exact-string match.</li>
 *   <li>Unmatched version throws {@link UnsupportedMinecraftVersionException} with the
 *       registered list embedded in the message.</li>
 *   <li>Duplicate programmatic registration is last-wins.</li>
 *   <li>An empty programmatic list falls back to ServiceLoader-discovered providers
 *       (verified via the {@code TestAssetProvider} declared in this test module's
 *       {@code META-INF/services/net.aerh.tessera.api.assets.AssetProvider} descriptor).</li>
 * </ul>
 */
class AssetProviderResolutionTest {

    private static AssetProvider stub(String version) {
        return new AssetProvider() {
            @Override
            public Set<String> supportedVersions() {
                return Set.of(version);
            }

            @Override
            public Path resolveAssetRoot(String v) {
                return Paths.get("/tmp/test-" + v);
            }

            @Override
            public Capabilities capabilities() {
                return new Capabilities(true, true, true, version);
            }
        };
    }

    @Test
    void builderRegisteredProviderResolvesByExactMatch() {
        AssetProvider p = stub("26.1.2");
        AssetProvider resolved = AssetProviderResolver.resolve(
                "26.1.2", List.of(p), "1.0.0-SNAPSHOT");
        assertThat(resolved).isSameAs(p);
    }

    @Test
    void unmatchedVersionThrowsWithRegisteredVersionsListed() {
        AssetProvider p = stub("26.1.2");
        assertThatThrownBy(() -> AssetProviderResolver.resolve(
                "27.0.0", List.of(p), "1.0.0-SNAPSHOT"))
                .isInstanceOf(UnsupportedMinecraftVersionException.class)
                .hasMessageContaining("27.0.0")
                .hasMessageContaining("26.1.2");
    }

    @Test
    void duplicateProgrammaticRegistrationsLastWins() {
        AssetProvider first = stub("26.1.2");
        AssetProvider second = stub("26.1.2");
        AssetProvider resolved = AssetProviderResolver.resolve(
                "26.1.2", List.of(first, second), "1.0.0-SNAPSHOT");
        assertThat(resolved).isSameAs(second);
    }

    @Test
    void emptyBuilderListFallsBackToServiceLoader() {
        // The TestAssetProvider declared in src/test/resources/META-INF/services/
        // net.aerh.tessera.api.assets.AssetProvider supplies "test-26.x" so the
        // ServiceLoader fallback resolves without depending on tessera-assets-26.1.2.
        AssetProvider resolved = AssetProviderResolver.resolve(
                "test-26.x", List.of(), "1.0.0-SNAPSHOT");
        assertThat(resolved.supportedVersions()).contains("test-26.x");
    }

    @Test
    void emptyBuilderListAndNoMatchingServiceLoaderThrows() {
        // 99.99.99 is not registered anywhere; verify the rich message + exception type.
        assertThatThrownBy(() -> AssetProviderResolver.resolve(
                "99.99.99", List.of(), "1.0.0-SNAPSHOT"))
                .isInstanceOf(UnsupportedMinecraftVersionException.class)
                .hasMessageContaining("99.99.99");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatThrownBy(() -> AssetProviderResolver.resolve(null, List.of(), "1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AssetProviderResolver.resolve("v", null, "1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AssetProviderResolver.resolve("v", List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
