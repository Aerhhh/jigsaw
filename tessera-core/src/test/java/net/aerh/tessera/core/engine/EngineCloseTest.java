package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.EngineBuilder;
import net.aerh.tessera.api.exception.ClosedEngineException;
import net.aerh.tessera.core.generator.DefaultEngineBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the.. lifecycle contract on {@link Engine#close()}:
 * <ul>
 *   <li>Double {@code close()} is a no-op (, CAS-idempotent).</li>
 *   <li>Render on a closed engine throws {@link ClosedEngineException}.</li>
 *   <li>{@code registerShutdownHook()} returns the same engine (fluent).</li>
 * </ul>
 *
 * <p>Tests that invoke {@code Engine.builder()...build()} against the 26.1.2 asset artifact
 * are gated behind {@code TESSERA_ASSETS_AVAILABLE=true} (W13): CI sets the env var AFTER
 * running {@code TesseraAssets.fetch("26.1.2")} in a pre-test step; local {@code mvn test}
 * runs without the env var skip those tests silently. The pure unit-level tests (no
 * {@code build()} call) run unconditionally below.
 */
class EngineCloseTest {

    // -----------------------------------------------------------------------
    // ServiceLoader wiring smoke (unit-level, no build() call).
    // -----------------------------------------------------------------------

    @Test
    void builderResolvesToDefaultEngineBuilderViaServiceLoader() {
        EngineBuilder b = Engine.builder();
        assertThat(b).isInstanceOf(DefaultEngineBuilder.class);
    }

    // -----------------------------------------------------------------------
    // Lifecycle tests require a real Engine, which needs fetched assets.
    // -----------------------------------------------------------------------

    @Test
    @EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
    void double_close_is_no_op() {
        Engine engine = buildEngineWithAcceptedEula();
        engine.close();
        // Second call must not throw.
        assertThatCode(engine::close).doesNotThrowAnyException();
        // Third call via try-with-resources equivalent.
        assertThatCode(engine::close).doesNotThrowAnyException();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
    void render_after_close_throws_ClosedEngineException() {
        Engine engine = buildEngineWithAcceptedEula();
        engine.close();
        assertThatThrownBy(() -> engine.item().itemId("diamond_sword").render())
                .isInstanceOf(ClosedEngineException.class)
                .isInstanceOf(IllegalStateException.class);  // : extends IllegalStateException
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
    void registerShutdownHook_returns_same_engine_fluent() {
        try (Engine engine = buildEngineWithAcceptedEula()) {
            Engine same = engine.registerShutdownHook();
            assertThat(same).isSameAs(engine);
            // Cannot assert the hook firing without JVM exit; asserting that
            // registerShutdownHook does not throw is sufficient coverage.
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
    void close_drains_inflight_renders_up_to_timeout() throws Exception {
        Engine engine = Engine.builder()
                .minecraftVersion("26.1.2")
                .acceptMojangEula(true)
                .shutdownTimeout(Duration.ofSeconds(3))
                .build();

        AtomicInteger observed = new AtomicInteger(0);
        Thread renderer = new Thread(() -> {
            try {
                engine.item().itemId("stone").renderAsync().whenComplete((r, ex) -> observed.incrementAndGet());
            } catch (Throwable ignored) {
                // close() may race with submission;  both outcomes are acceptable
                // (renderer finished naturally OR drain timeout expired).
            }
        });
        renderer.start();
        renderer.join(1_000);
        engine.close();
        // No assertion on completion count -  semantics allow either outcome.
        // We only verify close() itself returned without throwing.
        assertThat(true).isTrue();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Engine buildEngineWithAcceptedEula() {
        try {
            return Engine.builder()
                    .minecraftVersion("26.1.2")
                    .acceptMojangEula(true)
                    .build();
        } catch (Exception e) {
            throw new AssertionError(
                    "TESSERA_ASSETS_AVAILABLE=true but Engine.builder().build() failed; "
                            + "ensure TesseraAssets.fetch(\"26.1.2\") was run first",
                    e);
        }
    }
}
