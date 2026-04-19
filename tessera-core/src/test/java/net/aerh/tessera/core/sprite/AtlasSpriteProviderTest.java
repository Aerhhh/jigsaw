package net.aerh.tessera.core.sprite;

import net.aerh.tessera.core.testsupport.LiveAssetProviderResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Env-gated on {@code TESSERA_ASSETS_AVAILABLE=true}: depends on the hydrated atlas image
 * + coordinates JSON that {@code TesseraAtlasBuilder} writes into
 * {@code ~/.tessera/assets/26.1.2/tessera/atlas/}. Skips silently without the cache.
 */
@EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
class AtlasSpriteProviderTest {

    private static AtlasSpriteProvider provider;

    @BeforeAll
    static void setUp() {
        provider = AtlasSpriteProvider.fromAssetProvider(
                LiveAssetProviderResolver.resolve26_1_2(),
                LiveAssetProviderResolver.MC_VER);
    }

    @Test
    void loadsFromDefaults() {
        assertThat(provider.availableSprites()).isNotEmpty();
    }

    @Test
    void getKnownSprite_diamondSword() {
        Optional<BufferedImage> sprite = provider.getSprite("diamond_sword");
        assertThat(sprite).isPresent();
        assertThat(sprite.get().getWidth()).isGreaterThan(0);
        assertThat(sprite.get().getHeight()).isGreaterThan(0);
    }

    @Test
    void unknownSpriteReturnsEmpty() {
        assertThat(provider.getSprite("this_does_not_exist_xyz")).isEmpty();
    }

    @Test
    void searchFindsPartialMatch() {
        // "sword" should match "diamond_sword", "iron_sword", etc.
        Optional<BufferedImage> result = provider.search("sword");
        assertThat(result).isPresent();
    }

    @Test
    void searchWithNoMatchReturnsEmpty() {
        assertThat(provider.search("zzz_no_such_item_zzz")).isEmpty();
    }

    @Test
    void spriteIsCached_returnsSameInstance() {
        Optional<BufferedImage> first = provider.getSprite("diamond_sword");
        Optional<BufferedImage> second = provider.getSprite("diamond_sword");
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get()).isSameAs(second.get());
    }

    @Test
    void availableSpritesContainsDiamondSword() {
        assertThat(provider.availableSprites()).contains("diamond_sword");
    }
}
