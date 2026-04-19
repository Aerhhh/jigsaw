package net.aerh.tessera.integration;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.nbt.NbtFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the fluent {@link Engine} API. The original
 * body exercised {@code engine.render(RenderRequest)} + {@code engine.renderItem(String)}
 * surfaces that have since been removed; this rewrite exercises every fluent entry point
 * now on the public api.
 *
 * <p>Env-gated on {@code TESSERA_ASSETS_AVAILABLE=true} because the live item /
 * tooltip / inventory renders require the 26.1.2 asset cache. CI runs this
 * suite with the env var set; local {@code mvn test} skips silently when
 * {@code TesseraAssets.fetch("26.1.2")} has not been run.
 */
@EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class EngineIntegrationTest {

    private Engine engine;

    @BeforeAll
    void bootEngine() throws Exception {
        engine = Engine.builder()
                .minecraftVersion("26.1.2")
                .acceptMojangEula(true)
                .build();
    }

    @AfterAll
    void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    // -----------------------------------------------------------------------
    // Per-entry-point smokes
    // -----------------------------------------------------------------------

    @Test
    void item_render_returns_static_image() {
        GeneratorResult result = engine.item().itemId("apple").render();
        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
    }

    @Test
    void item_to_bytes_is_non_empty_png() {
        GeneratorResult result = engine.item().itemId("apple").render();
        GeneratorResult.StaticImage img = (GeneratorResult.StaticImage) result;
        byte[] png = img.toBytes();
        assertThat(png).isNotEmpty();
        // PNG magic number: 89 50 4E 47 0D 0A 1A 0A
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(png[1] & 0xFF).isEqualTo(0x50);
        assertThat(png[2] & 0xFF).isEqualTo(0x4E);
        assertThat(png[3] & 0xFF).isEqualTo(0x47);
    }

    @Test
    void tooltip_render_returns_static_image() {
        GeneratorResult result = engine.tooltip()
                .line("Legendary")
                .line("&7Damage: &c+25")
                .render();
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
    }

    @Test
    void inventory_render_returns_static_image() {
        GeneratorResult result = engine.inventory()
                .rows(3)
                .slotsPerRow(9)
                .title("Chest")
                .render();
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
    }

    @Test
    void composite_horizontal_render_returns_result() {
        GeneratorResult result = engine.composite()
                .horizontal()
                .add(engine.item().itemId("apple"))
                .add(engine.item().itemId("diamond_sword"))
                .render();
        assertThat(result).isNotNull();
    }

    @Test
    void composite_grid_render_returns_result() {
        GeneratorResult result = engine.composite()
                .grid(2, 2)
                .add(engine.item().itemId("apple"))
                .add(engine.item().itemId("diamond_sword"))
                .add(engine.item().itemId("stick"))
                .add(engine.item().itemId("emerald"))
                .render();
        assertThat(result).isNotNull();
    }

    @Test
    void fromNbt_explicit_snbt_format_renders() {
        GeneratorResult result = engine.fromNbt("{id:\"minecraft:apple\",count:1b}")
                .format(NbtFormat.SNBT)
                .render();
        assertThat(result).isNotNull();
    }

    @Test
    void fromNbt_auto_detect_renders_from_component_format() {
        // JSON_COMPONENT has the most specific detector signature (id+components).
        String componentNbt = "{\"id\":\"minecraft:apple\",\"components\":{}}";
        GeneratorResult result = engine.fromNbt(componentNbt).render();
        assertThat(result).isNotNull();
    }

    @Test
    void render_async_completes_within_timeout() throws Exception {
        CompletableFuture<GeneratorResult> future = engine.item()
                .itemId("apple")
                .renderAsync();
        GeneratorResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
    }

    @Test
    void item_result_cached_across_calls() {
        // Second render of the same request should hit the Caffeine cache (CachingGenerator
        // wraps every built-in generator ). We cannot observe the cache directly
        // without peeking internals; this test verifies at least that repeated calls
        // produce equivalent output without throwing.
        GeneratorResult first = engine.item().itemId("apple").render();
        GeneratorResult second = engine.item().itemId("apple").render();
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.firstFrame().getWidth()).isEqualTo(first.firstFrame().getWidth());
        assertThat(second.firstFrame().getHeight()).isEqualTo(first.firstFrame().getHeight());
    }
}
