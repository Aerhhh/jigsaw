package net.aerh.tessera.api.assets;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the {@code client_jar} extensions on the asset-fetch pipeline:
 * <ul>
 *   <li>{@link AssetManifest} parses a {@code client_jar} section from JSON.</li>
 *   <li>{@link AssetProvider#hydrate(HttpClient)} is a default no-op and can be safely
 *       ignored by older providers that carry no client.jar section.</li>
 * </ul>
 *
 * <p>The ServiceLoader-driven hydration loop in {@code TesseraAssets.fetch} is exercised
 * end-to-end by {@code Assets_26_1_2_Provider.hydrate} in the tessera-assets-26.1.2 module's
 * integration tests (env-gated on {@code TESSERA_ASSETS_AVAILABLE=true}); this test focuses on
 * the api-side contracts.
 */
class TesseraAssetsExtractionTest {

    @Test
    void asset_manifest_accepts_optional_client_jar() {
        ClientJarSection section = new ClientJarSection(
                "https://piston-data.mojang.com/v1/objects/deadbeef00000000000000000000000000000000/client.jar",
                "deadbeef00000000000000000000000000000000",
                1234567L);
        AssetManifest manifest = new AssetManifest("26.1.2", java.util.List.of(), section);

        assertThat(manifest.clientJar()).isSameAs(section);
        assertThat(manifest.optionalClientJar()).contains(section);
    }

    @Test
    void phase1_asset_manifest_without_client_jar_still_works() {
        AssetManifest manifest = new AssetManifest("1.21.4", java.util.List.of());

        assertThat(manifest.clientJar()).isNull();
        assertThat(manifest.optionalClientJar()).isEmpty();
    }

    @Test
    void default_hydrate_is_noop_for_phase1_providers() throws Exception {
        AssetProvider phase1Provider = new AssetProvider() {
            @Override public Set<String> supportedVersions() { return Set.of("1.21.4"); }
            @Override public Path resolveAssetRoot(String version) { return Path.of("/tmp/unused"); }
            @Override public Capabilities capabilities() { return new Capabilities(true, true, true, "1.21.4"); }
        };

        // Should complete without exception even with a null-ish HttpClient - the default
        // implementation never touches the argument.
        phase1Provider.hydrate(HttpClient.newHttpClient());
    }

    @Test
    void client_jar_section_rejects_malformed_sha1() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ClientJarSection("https://example.test/client.jar", "not-a-sha1", 0));
    }

    @Test
    void client_jar_section_rejects_negative_size() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ClientJarSection(
                        "https://example.test/client.jar",
                        "deadbeef00000000000000000000000000000000",
                        -1));
    }
}
