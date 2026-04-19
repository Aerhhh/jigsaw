package net.aerh.tessera.api;

import net.aerh.tessera.api.exception.TesseraAssetsMissingException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the contract on {@link EngineBuilder#build()}: when
 * {@link EngineBuilder#minecraftVersion(String)} is set but the resolved cache dir is cold,
 * {@code build()} throws {@link TesseraAssetsMissingException} whose message points consumers at
 * {@link net.aerh.tessera.api.assets.TesseraAssets#fetch(String)}.
 */
class EngineBuilderAssetsMissingTest {

    @Test
    void buildWithColdCacheThrowsAssetsMissing(@TempDir Path tempCache) {
        ThrowingCallable call = () ->
                Engine.builder()
                        .minecraftVersion("26.1.2")
                        .assetDir(tempCache)
                        .acceptMojangEula(true)
                        .build();

        assertThatThrownBy(call)
                .isInstanceOf(TesseraAssetsMissingException.class)
                .hasMessageContaining("TesseraAssets.fetch(\"26.1.2\")")
                .hasMessageContaining(tempCache.toString())
                .satisfies(ex -> {
                    var context = ((TesseraAssetsMissingException) ex).getContext();
                    assertThat(context).containsEntry("mcVer", "26.1.2");
                    assertThat(context).containsKey("cacheDir");
                    assertThat(context).containsKey("missingCount");
                });
    }

    @Test
    void buildWithoutMinecraftVersionThrowsIllegalState() {
        // minecraftVersion(...) is required as of 1.0.0. Omitting it previously fell
        // through to a classpath-backed deprecated-for-removal path that threw a cryptic
        // IllegalArgumentException at runtime once bundled Mojang bytes were stripped
        // from the published jars. Now we fail up-front with a clear diagnostic that
        // points the caller at TesseraAssets.fetch / the testingWithGenerator seam.
        assertThatThrownBy(() -> Engine.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minecraftVersion")
                .hasMessageContaining("required");
    }
}
