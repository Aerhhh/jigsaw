package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end verification of the wrap invariant: a checked
 * {@link RenderException} or {@link ParseException} thrown inside a
 * {@link Generator} MUST be wrapped as {@link RenderFailedException} when it
 * crosses the fluent {@code.render()} boundary so consumers never catch a
 * checked exception they cannot declare.
 *
 * <p>Uses the {@link DefaultEngineBuilder#testingWithGenerator(Class, Generator)}
 * test seam to replace the built-in item generator with a throwing stub; asserts
 * the throw path.
 *
 * <p>Env-gated on {@code TESSERA_ASSETS_AVAILABLE=true} because
 * {@code Engine.builder().minecraftVersion("26.1.2").build()} requires the
 * hydrated 26.1.2 asset cache. The invariant is otherwise covered at the
 * {@code *BuilderImpl} layer by {@code EngineFluentApiTest}'s no-throws
 * reflective gate - this test closes the end-to-end gap carryover
 * item #6.
 */
@EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
final class FluentRenderExceptionWrappingTest {

    @Test
    void checked_render_exception_inside_generator_wraps_as_render_failed() throws Exception {
        Generator<ItemRequest, GeneratorResult> throwingGen = new Generator<>() {
            @Override
            public GeneratorResult render(ItemRequest input, GenerationContext context) throws RenderException {
                throw new RenderException("simulated render failure",
                        Map.of("cause", "test-injection"));
            }

            @Override
            public Class<ItemRequest> inputType() { return ItemRequest.class; }

            @Override
            public Class<GeneratorResult> outputType() { return GeneratorResult.class; }
        };

        DefaultEngineBuilder db = (DefaultEngineBuilder) Engine.builder();
        db.minecraftVersion("26.1.2")
                .acceptMojangEula(true);
        db.testingWithGenerator(ItemRequest.class, throwingGen);

        try (Engine engine = db.build()) {
            assertThatThrownBy(() -> engine.item().itemId("apple").render())
                    .isInstanceOf(RenderFailedException.class)
                    .hasCauseInstanceOf(RenderException.class)
                    .hasMessageContaining("simulated render failure");
        }
    }

    @Test
    void checked_parse_exception_inside_generator_wraps_as_render_failed() throws Exception {
        Generator<ItemRequest, GeneratorResult> throwingGen = new Generator<>() {
            @Override
            public GeneratorResult render(ItemRequest input, GenerationContext context)
                    throws RenderException {
                // ParseException is checked. Tunnel it through RenderException so the
                // Generator contract (only RenderException declared) is preserved;
                // the engine's wrap path must surface RenderFailedException regardless.
                throw new RenderException("wrapped parse failure",
                        Map.of(), new ParseException("underlying parse issue"));
            }

            @Override
            public Class<ItemRequest> inputType() { return ItemRequest.class; }

            @Override
            public Class<GeneratorResult> outputType() { return GeneratorResult.class; }
        };

        DefaultEngineBuilder db = (DefaultEngineBuilder) Engine.builder();
        db.minecraftVersion("26.1.2")
                .acceptMojangEula(true);
        db.testingWithGenerator(ItemRequest.class, throwingGen);

        try (Engine engine = db.build()) {
            Throwable thrown = null;
            try {
                engine.item().itemId("apple").render();
            } catch (Throwable t) {
                thrown = t;
            }
            assertThat(thrown).isInstanceOf(RenderFailedException.class);
            assertThat(thrown.getCause()).isInstanceOf(RenderException.class);
            // The original ParseException is preserved as the RenderException's cause.
            assertThat(thrown.getCause().getCause()).isInstanceOf(ParseException.class);
        }
    }
}
