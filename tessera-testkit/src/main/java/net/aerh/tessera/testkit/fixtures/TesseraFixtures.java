package net.aerh.tessera.testkit.fixtures;

import java.util.List;
import java.util.Map;

/**
 * Curated render-input scenarios shared by Tessera's own fixture tests and by downstream
 * consumers that want coverage without hand-rolling every sample. Shipped in the main
 * scope of {@code tessera-testkit} so consumer {@code src/test} code can reference the
 * same canonical set.
 *
 * <p>Three catalogues:
 * <ul>
 *   <li>{@link #ITEM_IDS} - 10 item identifiers covering the common axes of variability
 *   (sword, food, tool, block, consumable, enchant-heavy, rarity-tinted, container,
 *   skull, bow).</li>
 *   <li>{@link #TOOLTIP_TEXT_SAMPLES} - 5+ tooltip strings covering §-codes, named
 *   colours, hex colours, multi-line lore, and rarity footers.</li>
 *   <li>{@link #ENCHANTMENT_COMBOS} - 3+ enchantment maps covering single, dual, and
 *   silk-touch conflicting combinations.</li>
 * </ul>
 *
 * <p>All collections are deeply immutable: {@link List#copyOf}/{@link Map#copyOf} returns
 * views that reject mutation at every level, so consumer code cannot tamper with the
 * canonical set.
 */
public final class TesseraFixtures {

    /**
     * Ten item identifiers chosen to exercise the common variability axes of the
     * item-render path.
     */
    public static final List<String> ITEM_IDS = List.of(
            "diamond_sword",
            "apple",
            "netherite_pickaxe",
            "oak_planks",
            "ender_pearl",
            "golden_apple",
            "enchanted_book",
            "shulker_box",
            "skull",
            "bow");

    /**
     * Tooltip text samples covering Minecraft's text-component variability:
     * legacy §-codes, named colours, hex colours (post-1.16), multi-line lore, and
     * rarity footers.
     */
    public static final List<String> TOOLTIP_TEXT_SAMPLES = List.of(
            "\u00A7bLegendary Sword\u00A7r",
            "{\"text\":\"Ender Warden\",\"color\":\"dark_purple\"}",
            "{\"text\":\"Custom\",\"color\":\"#ff00ff\"}",
            "Line one\nLine two\nLine three",
            "\u00A76Mythic\u00A7r");

    /**
     * Enchantment combinations covering the common conflict classes.
     */
    public static final List<Map<String, Integer>> ENCHANTMENT_COMBOS = List.of(
            Map.copyOf(Map.of("sharpness", 5, "unbreaking", 3)),
            Map.copyOf(Map.of("fortune", 3)),
            Map.copyOf(Map.of("silk_touch", 1, "efficiency", 4)));

    private TesseraFixtures() {
        /* utility class */
    }
}
