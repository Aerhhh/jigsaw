package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.generator.CompositeBuilder;
import net.aerh.tessera.api.generator.FromNbtBuilder;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.InventoryBuilder;
import net.aerh.tessera.api.generator.ItemBuilder;
import net.aerh.tessera.api.generator.PlayerHeadBuilder;
import net.aerh.tessera.api.generator.PlayerModelBuilder;
import net.aerh.tessera.api.generator.RenderSpec;
import net.aerh.tessera.api.generator.TooltipBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies: every public render entry point on {@link Engine} returns a fluent builder
 * implementing {@link RenderSpec}, and every builder declares both {@code render()} (sync) and
 * {@code renderAsync()} (async) terminals plus a {@code context()} self-returning setter.
 *
 * <p>This test asserts the contract via reflection, so it does not need a live engine — it
 * compiles iff the api surface is correct, and passes iff the methods have the expected
 * shapes.
 */
class EngineFluentApiTest {

    @Test
    void everyRenderEntryPointReturnsAFluentBuilder() throws NoSuchMethodException {
        Class<Engine> engineClass = Engine.class;

        assertThat(engineClass.getMethod("item").getReturnType()).isEqualTo(ItemBuilder.class);
        assertThat(engineClass.getMethod("tooltip").getReturnType()).isEqualTo(TooltipBuilder.class);
        assertThat(engineClass.getMethod("inventory").getReturnType()).isEqualTo(InventoryBuilder.class);
        assertThat(engineClass.getMethod("playerHead").getReturnType()).isEqualTo(PlayerHeadBuilder.class);
        assertThat(engineClass.getMethod("playerModel").getReturnType()).isEqualTo(PlayerModelBuilder.class);
        assertThat(engineClass.getMethod("composite").getReturnType()).isEqualTo(CompositeBuilder.class);
        assertThat(engineClass.getMethod("fromNbt", String.class).getReturnType())
                .isEqualTo(FromNbtBuilder.class);

        // Each builder implements RenderSpec so it can be composed.
        assertThat(RenderSpec.class.isAssignableFrom(ItemBuilder.class)).isTrue();
        assertThat(RenderSpec.class.isAssignableFrom(TooltipBuilder.class)).isTrue();
        assertThat(RenderSpec.class.isAssignableFrom(InventoryBuilder.class)).isTrue();
        assertThat(RenderSpec.class.isAssignableFrom(PlayerHeadBuilder.class)).isTrue();
        assertThat(RenderSpec.class.isAssignableFrom(PlayerModelBuilder.class)).isTrue();
        assertThat(RenderSpec.class.isAssignableFrom(CompositeBuilder.class)).isTrue();
        assertThat(RenderSpec.class.isAssignableFrom(FromNbtBuilder.class)).isTrue();
    }

    @Test
    void everyBuilderHasRenderAndRenderAsyncAndContextTerminals() throws NoSuchMethodException {
        for (Class<?> builder : List.of(ItemBuilder.class, TooltipBuilder.class,
                InventoryBuilder.class, PlayerHeadBuilder.class, PlayerModelBuilder.class,
                CompositeBuilder.class, FromNbtBuilder.class)) {

            assertThat(builder.getMethod("render").getReturnType())
                    .as("%s.render() returns GeneratorResult", builder.getSimpleName())
                    .isEqualTo(GeneratorResult.class);
            assertThat(builder.getMethod("render").getExceptionTypes())
                    .as("%s.render() declares no checked throws", builder.getSimpleName())
                    .isEmpty();

            assertThat(builder.getMethod("renderAsync").getReturnType())
                    .as("%s.renderAsync() returns CompletableFuture", builder.getSimpleName())
                    .isEqualTo(CompletableFuture.class);
            assertThat(builder.getMethod("renderAsync").getExceptionTypes())
                    .as("%s.renderAsync() declares no checked throws", builder.getSimpleName())
                    .isEmpty();

            assertThat(builder.getMethod("context", GenerationContext.class).getReturnType())
                    .as("%s.context(GenerationContext) returns Self", builder.getSimpleName())
                    .isEqualTo(builder);
        }
    }
}
