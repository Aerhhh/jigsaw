package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.effect.EffectContext;
import net.aerh.tessera.api.effect.ImageEffect;
import net.aerh.tessera.api.effect.MetadataKeys;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.sprite.SpriteProvider;
import net.aerh.tessera.core.effect.DurabilityBarEffect;
import net.aerh.tessera.core.effect.EffectPipeline;
import net.aerh.tessera.core.sprite.AtlasSpriteProvider;
import net.aerh.tessera.core.testsupport.LiveAssetProviderResolver;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.UnknownItemException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Env-gated on {@code TESSERA_ASSETS_AVAILABLE=true}: reads the hydrated atlas via
 * {@link AtlasSpriteProvider#fromAssetProvider}. Skips silently without the cache.
 */
@EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
class ItemGeneratorTest {

    private static SpriteProvider spriteProvider;

    private EffectPipeline emptyPipeline;
    private ItemGenerator generator;

    @BeforeAll
    static void initSpriteProvider() {
        spriteProvider = AtlasSpriteProvider.fromAssetProvider(
                LiveAssetProviderResolver.resolve26_1_2(),
                LiveAssetProviderResolver.MC_VER);
    }

    /**
     * Synthesised 16x16 glint texture so {@code new GlintEffect()} doesn't have to read the
     * (post--stripped) classpath resource. The item-generator tests care that the
     * pipeline emits animated output when glint is present, not about pixel-fidelity of
     * the shimmer.
     */
    private static BufferedImage syntheticGlintTexture() {
        BufferedImage tex = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int intensity = ((x + y) * 255 / 30) & 0xFF;
                int argb = (intensity << 24) | (intensity << 16) | (intensity << 8) | intensity;
                tex.setRGB(x, y, argb);
            }
        }
        return tex;
    }

    @BeforeEach
    void setUp() {
        emptyPipeline = EffectPipeline.builder().build();
        generator = new ItemGenerator(spriteProvider, emptyPipeline);
    }

    // --- Known item ---

    @Test
    void render_knownItemReturnsStaticImage() throws RenderException {
        ItemRequest request = ItemRequest.builder().itemId("diamond_sword").build();
        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
        assertThat(result.firstFrame().getHeight()).isGreaterThan(0);
        assertThat(result.isAnimated()).isFalse();
    }

    // --- Unknown item ---

    @Test
    void render_unknownItemThrowsRenderException() {
        ItemRequest request = ItemRequest.builder().itemId("totally_unknown_item_xyz").build();

        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class)
                .hasCauseInstanceOf(UnknownItemException.class);
    }

    @Test
    void render_unknownItemRenderExceptionContainsItemId() {
        ItemRequest request = ItemRequest.builder().itemId("missing_item").build();

        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class)
                .satisfies(ex -> {
                    RenderException renderEx = (RenderException) ex;
                    assertThat(renderEx.getContext()).containsKey("itemId");
                    assertThat(renderEx.getContext().get("itemId")).isEqualTo("missing_item");
                });
    }

    // --- scale upscaling ---

    @Test
    void render_scaleUpscalesByGivenFactor() throws RenderException {
        ItemRequest normal = ItemRequest.builder().itemId("diamond_sword").build();
        ItemRequest scaled = ItemRequest.builder().itemId("diamond_sword").scale(10).build();

        GeneratorResult normalResult = generator.render(normal, GenerationContext.defaults());
        GeneratorResult scaledResult = generator.render(scaled, GenerationContext.defaults());

        int normalW = normalResult.firstFrame().getWidth();
        int scaledW = scaledResult.firstFrame().getWidth();

        assertThat(scaledW).isEqualTo(normalW * 10);
    }

    // --- Effect pipeline execution ---

    @Test
    void render_effectPipelineIsExecuted() throws RenderException {
        List<String> log = new ArrayList<>();

        ImageEffect trackingEffect = new ImageEffect() {
            @Override public String id() { return "tracker"; }
            @Override public int priority() { return 100; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                log.add("executed");
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(trackingEffect).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline);

        gen.render(ItemRequest.builder().itemId("diamond_sword").build(), GenerationContext.defaults());

        assertThat(log).containsExactly("executed");
    }

    @Test
    void render_enchantedItemWithGlintReturnsAnimatedResult() throws RenderException {
        EffectPipeline glintPipeline = EffectPipeline.builder()
                .add(new net.aerh.tessera.core.effect.GlintEffect(syntheticGlintTexture()))
                .build();

        ItemGenerator gen = new ItemGenerator(spriteProvider, glintPipeline);
        ItemRequest request = ItemRequest.builder().itemId("diamond_sword").enchanted(true).build();

        GeneratorResult result = gen.render(request, GenerationContext.defaults());

        assertThat(result.isAnimated()).isTrue();
        assertThat(result).isInstanceOf(GeneratorResult.AnimatedImage.class);
    }

    @Test
    void render_durabilityMetadataIsPassedToPipeline() throws RenderException {
        // DurabilityBarEffect should apply when durabilityPercent is present
        final boolean[] applied = {false};

        ImageEffect durabilityDetector = new ImageEffect() {
            @Override public String id() { return "detector"; }
            @Override public int priority() { return 0; }
            @Override public boolean appliesTo(EffectContext ctx) {
                return ctx.metadata(MetadataKeys.DURABILITY_PERCENT, Double.class).isPresent();
            }
            @Override public EffectContext apply(EffectContext ctx) {
                applied[0] = true;
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(durabilityDetector).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline);

        ItemRequest request = ItemRequest.builder()
            .itemId("diamond_sword")
                .durabilityPercent(0.5)
                .build();

        gen.render(request, GenerationContext.defaults());

        assertThat(applied[0]).isTrue();
    }

    // --- inputType / outputType ---

    @Test
    void inputType_returnsItemRequestClass() {
        assertThat(generator.inputType()).isEqualTo(ItemRequest.class);
    }

    @Test
    void outputType_returnsGeneratorResultClass() {
        assertThat(generator.outputType()).isEqualTo(GeneratorResult.class);
    }

    // --- null guards ---

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> generator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        ItemRequest request = ItemRequest.builder().itemId("diamond_sword").build();
        assertThatThrownBy(() -> generator.render(request, null))
                .isInstanceOf(NullPointerException.class);
    }
}
