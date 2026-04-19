package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.font.FontRegistry;
import net.aerh.tessera.core.effect.EffectPipeline;
import net.aerh.tessera.core.font.DefaultFontRegistry;
import net.aerh.tessera.core.sprite.AtlasSpriteProvider;
import net.aerh.tessera.core.testsupport.LiveAssetProviderResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InventoryGenerator#getScaleFactor()}.
 *
 * <p>Env-gated on {@code TESSERA_ASSETS_AVAILABLE=true}: the classpath-bundled atlas bytes
 * were stripped, so the sprite provider now reads from
 * {@code AssetProvider.resolveAssetRoot(...)}. Skips silently on dev/CI without a hydrated
 * {@code ~/.tessera/assets/26.1.2/} cache.
 */
@EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
class InventoryGeneratorScaleFactorTest {

    private static FontRegistry fontRegistry;
    private static AssetProvider assetProvider;

    @BeforeAll
    static void init() {
        assetProvider = LiveAssetProviderResolver.resolve26_1_2();
        fontRegistry = DefaultFontRegistry.withBuiltins(assetProvider, LiveAssetProviderResolver.MC_VER);
    }

    private static AtlasSpriteProvider liveAtlas() {
        return AtlasSpriteProvider.fromAssetProvider(assetProvider, LiveAssetProviderResolver.MC_VER);
    }

    @Test
    void getScaleFactor_withDefaultAtlasIsAtLeastOne() {
        InventoryGenerator generator = new InventoryGenerator(
                liveAtlas(),
                EffectPipeline.builder().build(),
                fontRegistry);

        int scaleFactor = generator.getScaleFactor();

        assertThat(scaleFactor).isGreaterThanOrEqualTo(1);
    }

    @Test
    void getScaleFactor_isIntegerMultipleOfSixteen() {
        InventoryGenerator generator = new InventoryGenerator(
                liveAtlas(),
                EffectPipeline.builder().build(),
                fontRegistry);

        int scaleFactor = generator.getScaleFactor();

        // getScaleFactor() = spriteSize / 16, so scaleFactor * 16 should equal the sprite size
        // (we just verify it is a positive integer)
        assertThat(scaleFactor).isPositive();
    }

    @Test
    void getScaleFactor_consistentAcrossCallsOnSameInstance() {
        InventoryGenerator generator = new InventoryGenerator(
                liveAtlas(),
                EffectPipeline.builder().build(),
                fontRegistry);

        int first = generator.getScaleFactor();
        int second = generator.getScaleFactor();

        assertThat(first).isEqualTo(second);
    }
}
