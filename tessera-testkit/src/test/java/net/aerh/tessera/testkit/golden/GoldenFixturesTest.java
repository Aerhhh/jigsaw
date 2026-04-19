package net.aerh.tessera.testkit.golden;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.exception.TesseraAssetsMissingException;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.testkit.fixtures.TesseraFixtures;
import net.aerh.tessera.testkit.image.GoldenImageAssertion;
import net.aerh.tessera.testkit.image.GoldenImageConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Orchestrator for the full golden-fixture assertion suite covering items, tooltips,
 * inventories, player heads, player models, composites, and hover/selection overlays.
 *
 * <p><strong>Shipped without captured baselines.</strong> The test class, parameterised
 * structure, helper methods, directory scaffolding, and env-gate are all in place. Actual
 * baseline PNG / WebP / GIF capture is deferred to a later calibration pass that runs with
 * a hydrated {@code ~/.tessera/assets/26.1.2/} cache and writes baselines via
 * {@code -Dtessera.golden.update=true}.
 *
 * <p>Lives in tessera-testkit rather than tessera-core because testkit already has a
 * test-scope dep on tessera-core and owns the {@code GoldenImageAssertion} surface; the
 * inverse (tessera-core depending on testkit) would introduce a reactor-level cyclic
 * dependency. Test-only so it never ships in the distributed testkit jar.
 *
 * <p>The class-level {@link EnabledIfEnvironmentVariable} gate skips every test method
 * silently on stock CI and dev hosts that have not run {@code TesseraAssets.fetch}.
 * On hosts with the cache, each test:
 * <ol>
 *   <li>Builds a fresh {@link Engine} via the fluent api.</li>
 *   <li>Renders the request using the corresponding fluent builder.</li>
 *   <li>Looks up the baseline PNG under
 *   {@code src/test/resources/golden/26.1.2/&lt;render-type&gt;/&lt;scenario&gt;.png}.</li>
 *   <li>If {@code -Dtessera.golden.update=true} is set (and {@code CI} is not),
 *   regenerates the baseline via {@link GoldenImageAssertion#updateIfRequested}.</li>
 *   <li>Otherwise asserts via the two-gate
 *   {@link GoldenImageAssertion#assertGoldenEquals(BufferedImage, BufferedImage, double, double)}
 *   with the per-render-type SSIM floor from {@link GoldenImageConfig}.</li>
 * </ol>
 *
 * <p>Animated goldens (glint, obfuscation) use first-frame comparison today; full
 * multi-frame SSIM / WebP / GIF comparison will land with the calibration pass.
 */
@EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
class GoldenFixturesTest {

    /** Root of the committed baseline tree (relative to the testkit module root). */
    private static final Path GOLDEN_ROOT =
            Paths.get("src", "test", "resources", "golden", "26.1.2");

    private static Engine engine;

    @BeforeAll
    static void bootEngine() throws TesseraAssetsMissingException {
        engine = Engine.builder()
                .minecraftVersion("26.1.2")
                .acceptMojangEula(true)
                .build();
    }

    @AfterAll
    static void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    // -----------------------------------------------------------------------
    // Static items
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "diamond_sword", "apple", "netherite_pickaxe", "oak_planks", "ender_pearl",
            "golden_apple", "enchanted_book", "shulker_box", "bow", "stick"
    })
    void item_golden(String itemId) throws IOException {
        BufferedImage actual = engine.item().itemId(itemId).render().firstFrame();
        assertAgainstGolden(actual, "item/" + itemId + ".png", GoldenImageConfig.ITEM_ICON_SSIM);
    }

    @Test
    void item_overlay_durability_045_golden() throws IOException {
        BufferedImage actual = engine.item()
                .itemId("diamond_sword")
                .durabilityPercent(0.45)
                .render()
                .firstFrame();
        assertAgainstGolden(actual, "item/overlay_durability_045.png", GoldenImageConfig.ITEM_ICON_SSIM);
    }

    @Test
    void item_overlay_dye_orange_golden() throws IOException {
        BufferedImage actual = engine.item()
                .itemId("leather_helmet")
                .dyeColor(0xFF9500)
                .render()
                .firstFrame();
        assertAgainstGolden(actual, "item/overlay_dye_orange.png", GoldenImageConfig.ITEM_ICON_SSIM);
    }

    // -----------------------------------------------------------------------
    // Enchanted-glint first-frame baseline
    // -----------------------------------------------------------------------

    @Test
    void glint_first_frame_golden() throws IOException {
        GeneratorResult result = engine.item()
                .itemId("diamond_sword")
                .enchanted(true)
                .render();
        BufferedImage actual = result.firstFrame();
        assertAgainstGolden(actual, "item/enchanted_diamond_sword_frame00.png",
                GoldenImageConfig.ITEM_ICON_SSIM);
    }

    // -----------------------------------------------------------------------
    // Tooltip scenarios (drives the 5 text samples shipped by TesseraFixtures)
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void tooltip_golden(int sampleIndex) throws IOException {
        String text = TesseraFixtures.TOOLTIP_TEXT_SAMPLES.get(sampleIndex);
        BufferedImage actual = engine.tooltip().line(text).render().firstFrame();
        assertAgainstGolden(actual, "tooltip/sample_" + sampleIndex + ".png",
                GoldenImageConfig.TOOLTIP_SSIM);
    }

    // -----------------------------------------------------------------------
    // Inventory containers (dimensions per container type)
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"chest_3x9", "player_3x9", "hopper_1x5", "furnace_1x3", "brewing_1x4"})
    void inventory_golden(String containerType) throws IOException {
        int[] dims = switch (containerType) {
            case "chest_3x9", "player_3x9" -> new int[]{3, 9};
            case "hopper_1x5" -> new int[]{1, 5};
            case "furnace_1x3" -> new int[]{1, 3};
            case "brewing_1x4" -> new int[]{1, 4};
            default -> throw new IllegalArgumentException("unknown container: " + containerType);
        };
        BufferedImage actual = engine.inventory()
                .rows(dims[0])
                .slotsPerRow(dims[1])
                .title(containerType)
                .render()
                .firstFrame();
        assertAgainstGolden(actual, "inventory/" + containerType + ".png",
                GoldenImageConfig.INVENTORY_SSIM);
    }

    // -----------------------------------------------------------------------
    // Player heads - scaffolded; live-skin wiring lands with calibration
    // -----------------------------------------------------------------------

    @Test
    void playerhead_scaffold_exists() {
        Assumptions.assumeTrue(
                Files.exists(GOLDEN_ROOT.resolve("playerhead/.gitkeep")),
                "playerhead/ scaffold missing (expected .gitkeep under golden/26.1.2/playerhead/)");
    }

    // -----------------------------------------------------------------------
    // Player models - scaffolded; pose wiring lands with calibration
    // -----------------------------------------------------------------------

    @Test
    void playermodel_scaffold_exists() {
        Assumptions.assumeTrue(
                Files.exists(GOLDEN_ROOT.resolve("playermodel/.gitkeep")),
                "playermodel/ scaffold missing (expected .gitkeep under golden/26.1.2/playermodel/)");
    }

    // -----------------------------------------------------------------------
    // Composite (grid + horizontal + vertical)
    // -----------------------------------------------------------------------

    @Test
    void composite_horizontal_golden() throws IOException {
        BufferedImage actual = engine.composite()
                .horizontal()
                .add(engine.item().itemId("diamond_sword"))
                .add(engine.item().itemId("apple"))
                .render()
                .firstFrame();
        assertAgainstGolden(actual, "composite/horizontal.png", GoldenImageConfig.ITEM_ICON_SSIM);
    }

    @Test
    void composite_vertical_golden() throws IOException {
        BufferedImage actual = engine.composite()
                .vertical()
                .add(engine.item().itemId("diamond_sword"))
                .add(engine.item().itemId("apple"))
                .render()
                .firstFrame();
        assertAgainstGolden(actual, "composite/vertical.png", GoldenImageConfig.ITEM_ICON_SSIM);
    }

    @Test
    void composite_grid_2x2_golden() throws IOException {
        BufferedImage actual = engine.composite()
                .grid(2, 2)
                .add(engine.item().itemId("diamond_sword"))
                .add(engine.item().itemId("apple"))
                .add(engine.item().itemId("golden_apple"))
                .add(engine.item().itemId("stick"))
                .render()
                .firstFrame();
        assertAgainstGolden(actual, "composite/grid_2x2.png", GoldenImageConfig.ITEM_ICON_SSIM);
    }

    // -----------------------------------------------------------------------
    // Hover / selection overlay - scaffolded
    // -----------------------------------------------------------------------

    @Test
    void inventory_selected_slot_golden_scaffold() {
        Assumptions.assumeTrue(
                Files.exists(GOLDEN_ROOT.resolve("hover/.gitkeep")),
                "hover/ scaffold missing (expected .gitkeep under golden/26.1.2/hover/)");
    }

    // -----------------------------------------------------------------------
    // Helper: two-gate assertion with baseline regeneration when the flag is set.
    // -----------------------------------------------------------------------

    private static void assertAgainstGolden(BufferedImage actual, String relPath, double ssimFloor)
            throws IOException {
        Path goldenPath = GOLDEN_ROOT.resolve(relPath);

        if (GoldenImageAssertion.updateIfRequested(actual, goldenPath)) {
            // Baseline regenerated by `-Dtessera.golden.update=true`; nothing to assert.
            return;
        }
        if (!Files.exists(goldenPath)) {
            // Baseline not yet captured (expected until calibration runs). Skip gracefully
            // via JUnit's Assumptions so the test reports SKIPPED rather than FAILED on
            // hosts that have the cache but haven't generated baselines yet.
            Assumptions.abort("Golden baseline missing: " + goldenPath
                    + " (capture via -Dtessera.golden.update=true)");
            return;
        }
        BufferedImage expected = ImageIO.read(goldenPath.toFile());
        // Route through the 5-arg overload passing relPath as the baselineId so the
        // capture-baselines workflow can grep per-fixture SSIM from
        // surefire-reports/*output.txt during calibration.
        GoldenImageAssertion.assertGoldenEquals(actual, expected, ssimFloor,
                GoldenImageConfig.MAX_HOTSPOT_PCT, relPath);
    }
}
