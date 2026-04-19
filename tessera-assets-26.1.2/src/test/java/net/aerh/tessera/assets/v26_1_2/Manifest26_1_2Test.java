package net.aerh.tessera.assets.v26_1_2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies  + : the regenerated 26.1.2 manifest is on the artifact's classpath,
 * carries the correct version string, has the same schema as the earlier 1.21.4 manifest
 * (path/sha1/size/url per entry), and the {@link Assets_26_1_2_Provider} declares the
 * correct version + capabilities.
 */
class Manifest26_1_2Test {

    private static final Pattern SHA1_PATTERN = Pattern.compile("^[0-9a-f]{40}$");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https://resources\\.download\\.minecraft\\.net/[0-9a-f]{2}/[0-9a-f]{40}$");

    @Test
    void manifestLoadsFromClasspathWithVersion26_1_2() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("tessera/assets/26.1.2/manifest.json")) {
            assertThat(in).as("manifest.json must be on the classpath").isNotNull();
            JsonObject manifest = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            assertThat(manifest.get("version").getAsString()).isEqualTo("26.1.2");
            JsonArray files = manifest.getAsJsonArray("files");
            assertThat(files).isNotNull();
            assertThat(files.size()).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void everyFileEntryHasSha1AndUrlFields() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("tessera/assets/26.1.2/manifest.json")) {
            JsonObject manifest = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray files = manifest.getAsJsonArray("files");
            for (var entry : files) {
                JsonObject f = entry.getAsJsonObject();
                assertThat(f.has("path")).isTrue();
                assertThat(f.has("sha1")).isTrue();
                assertThat(f.has("size")).isTrue();
                assertThat(f.has("url")).isTrue();
                assertThat(SHA1_PATTERN.matcher(f.get("sha1").getAsString()).matches())
                        .as("sha1 must be 40-hex-lowercase: %s", f.get("sha1").getAsString())
                        .isTrue();
                assertThat(URL_PATTERN.matcher(f.get("url").getAsString()).matches())
                        .as("url must follow piston-meta CDN layout: %s", f.get("url").getAsString())
                        .isTrue();
                assertThat(f.get("url").getAsString()).endsWith(f.get("sha1").getAsString());
                assertThat(f.get("path").getAsString())
                        .doesNotContain("..").doesNotContain("\\").doesNotStartWith("/");
                assertThat(f.get("size").getAsLong()).isNotNegative();
            }
        }
    }

    @Test
    void assets26_1_2ProviderDeclaresExactVersionAndCapabilities() {
        Assets_26_1_2_Provider provider = new Assets_26_1_2_Provider();
        assertThat(provider.supportedVersions()).containsExactly("26.1.2");
        assertThat(provider.capabilities().mcVersion()).isEqualTo("26.1.2");
        assertThat(provider.capabilities().hasComponents()).isTrue();
        assertThat(provider.capabilities().hasFlattening()).isTrue();
        assertThat(provider.capabilities().supportsAnimatedInventory()).isTrue();
    }

    @Test
    void resolveAssetRootRejectsUnsupportedVersion() {
        Assets_26_1_2_Provider provider = new Assets_26_1_2_Provider();
        try {
            provider.resolveAssetRoot("27.0.0");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("26.1.2").hasMessageContaining("27.0.0");
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for unsupported version");
    }

    @Test
    void manifestCarriesClientJarSectionFromPistonMeta() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("tessera/assets/26.1.2/manifest.json")) {
            JsonObject manifest = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject clientJar = manifest.getAsJsonObject("client_jar");
            assertThat(clientJar).as("manifest must carry a client_jar section for hydration")
                    .isNotNull();
            assertThat(clientJar.has("url")).isTrue();
            assertThat(clientJar.has("sha1")).isTrue();
            assertThat(clientJar.has("size")).isTrue();
            assertThat(SHA1_PATTERN.matcher(clientJar.get("sha1").getAsString()).matches()).isTrue();
            assertThat(clientJar.get("url").getAsString())
                    .startsWith("https://piston-data.mojang.com/");
            assertThat(clientJar.get("size").getAsLong()).isPositive();
        }
    }
}
