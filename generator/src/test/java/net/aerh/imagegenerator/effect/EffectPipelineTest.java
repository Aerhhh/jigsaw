package net.aerh.imagegenerator.effect;

import net.aerh.imagegenerator.effect.impl.DurabilityBarEffect;
import net.aerh.imagegenerator.effect.impl.GlintImageEffect;
import net.aerh.imagegenerator.effect.impl.HoverImageEffect;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class EffectPipelineTest {

    private static BufferedImage solidImage(int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, y, color.getRGB());
            }
        }
        return img;
    }

    private static EffectContext baseContext(boolean enchanted, boolean hovered) {
        return new EffectContext.Builder()
            .withImage(solidImage(16, Color.RED))
            .withItemId("test_item")
            .withEnchanted(enchanted)
            .withHovered(hovered)
            .build();
    }

    // Pipeline ordering

    @Test
    void effectsExecuteInPriorityOrder() {
        StringBuilder order = new StringBuilder();

        ImageEffect first = new TestEffect("A", 10, order);
        ImageEffect second = new TestEffect("B", 50, order);
        ImageEffect third = new TestEffect("C", 100, order);

        EffectPipeline pipeline = new EffectPipeline.Builder()
            .addEffect(third)  // added out of order
            .addEffect(first)
            .addEffect(second)
            .build();

        pipeline.execute(baseContext(false, false));

        assertThat(order.toString()).isEqualTo("A,B,C,");
    }

    // canApply gating

    @Test
    void glintOnlyAppliesWhenEnchanted() {
        GlintImageEffect glint = new GlintImageEffect(solidImage(8, Color.WHITE));

        assertThat(glint.canApply(baseContext(true, false))).isTrue();
        assertThat(glint.canApply(baseContext(false, false))).isFalse();
    }

    @Test
    void hoverOnlyAppliesWhenHovered() {
        HoverImageEffect hover = new HoverImageEffect();

        assertThat(hover.canApply(baseContext(false, true))).isTrue();
        assertThat(hover.canApply(baseContext(false, false))).isFalse();
    }

    @Test
    void durabilityOnlyAppliesWhenBelow100() {
        DurabilityBarEffect durability = new DurabilityBarEffect();

        EffectContext withDurability = new EffectContext.Builder()
            .withImage(solidImage(16, Color.RED))
            .withItemId("test")
            .putMetadata("durabilityPercent", 50)
            .build();

        EffectContext fullDurability = new EffectContext.Builder()
            .withImage(solidImage(16, Color.RED))
            .withItemId("test")
            .putMetadata("durabilityPercent", 100)
            .build();

        EffectContext noDurability = new EffectContext.Builder()
            .withImage(solidImage(16, Color.RED))
            .withItemId("test")
            .build();

        assertThat(durability.canApply(withDurability)).isTrue();
        assertThat(durability.canApply(fullDurability)).isFalse();
        assertThat(durability.canApply(noDurability)).isFalse();
    }

    // Effect application

    @Test
    void hoverEffectChangesPixels() {
        HoverImageEffect hover = new HoverImageEffect();
        EffectContext ctx = baseContext(false, true);
        int originalPixel = ctx.getImage().getRGB(0, 0);

        EffectResult result = hover.apply(ctx);

        assertThat(result.getImage().getRGB(0, 0)).isNotEqualTo(originalPixel);
    }

    @Test
    void hoverEffectPreservesDimensions() {
        HoverImageEffect hover = new HoverImageEffect();
        EffectContext ctx = baseContext(false, true);

        EffectResult result = hover.apply(ctx);

        assertThat(result.getImage().getWidth()).isEqualTo(16);
        assertThat(result.getImage().getHeight()).isEqualTo(16);
    }

    @Test
    void durabilityEffectPreservesDimensions() {
        DurabilityBarEffect durability = new DurabilityBarEffect();
        EffectContext ctx = new EffectContext.Builder()
            .withImage(solidImage(16, Color.BLUE))
            .withItemId("test")
            .putMetadata("durabilityPercent", 50)
            .build();

        EffectResult result = durability.apply(ctx);

        assertThat(result.getImage().getWidth()).isEqualTo(16);
        assertThat(result.getImage().getHeight()).isEqualTo(16);
    }

    @Test
    void glintEffectProducesAnimatedResult() {
        GlintImageEffect glint = new GlintImageEffect(solidImage(8, Color.WHITE));
        EffectContext ctx = baseContext(true, false);

        EffectResult result = glint.apply(ctx);

        assertThat(result.isAnimated()).isTrue();
        assertThat(result.getAnimationFrames()).isNotEmpty();
        assertThat(result.getFrameDelayMs()).isGreaterThan(0);
    }

    @Test
    void skippedEffectPassesThroughUnchanged() {
        HoverImageEffect hover = new HoverImageEffect();
        EffectPipeline pipeline = new EffectPipeline.Builder()
            .addEffect(hover)
            .build();

        EffectContext ctx = baseContext(false, false); // hovered=false
        EffectContext result = pipeline.execute(ctx);

        // Image should be unchanged since hover was skipped
        assertThat(result.getImage().getRGB(0, 0)).isEqualTo(ctx.getImage().getRGB(0, 0));
    }

    @Test
    void nullEffectsIgnored() {
        EffectPipeline pipeline = new EffectPipeline.Builder()
            .addEffect(null)
            .addEffect(new HoverImageEffect())
            .build();

        // Should not throw
        EffectContext result = pipeline.execute(baseContext(false, true));
        assertThat(result.getImage()).isNotNull();
    }

    // Priority values

    @Test
    void builtInEffectPriorities() {
        assertThat(new HoverImageEffect().getPriority()).isEqualTo(200);
        assertThat(new DurabilityBarEffect().getPriority()).isEqualTo(300);
    }

    private static class TestEffect implements ImageEffect {
        private final String name;
        private final int priority;
        private final StringBuilder order;

        TestEffect(String name, int priority, StringBuilder order) {
            this.name = name;
            this.priority = priority;
            this.order = order;
        }

        @Override
        public EffectResult apply(EffectContext context) {
            order.append(name).append(",");
            return EffectResult.single(context.getImage());
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getPriority() {
            return priority;
        }
    }
}
