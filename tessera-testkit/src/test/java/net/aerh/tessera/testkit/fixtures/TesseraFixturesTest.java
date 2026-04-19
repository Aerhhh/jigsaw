package net.aerh.tessera.testkit.fixtures;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD RED-first specification for {@link TesseraFixtures}.
 *
 * <p>The fixture catalogue must be immutable (consumers can read but not mutate) and cover
 * enough breadth to exercise the render-primitive waves (item IDs, tooltip text variants,
 * enchantment combos).
 */
class TesseraFixturesTest {

    @Test
    void item_ids_contains_core_entries() {
        List<String> ids = TesseraFixtures.ITEM_IDS;
        assertThat(ids).contains("diamond_sword", "apple", "netherite_pickaxe", "skull");
        assertThat(ids).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void enchantment_combos_non_empty() {
        List<Map<String, Integer>> combos = TesseraFixtures.ENCHANTMENT_COMBOS;
        assertThat(combos).hasSizeGreaterThanOrEqualTo(3);
        combos.forEach(combo -> assertThat(combo).isNotNull());
    }

    @Test
    void tooltip_text_samples_not_empty() {
        List<String> samples = TesseraFixtures.TOOLTIP_TEXT_SAMPLES;
        assertThat(samples).hasSizeGreaterThanOrEqualTo(5);
        samples.forEach(s -> assertThat(s).isNotNull().isNotBlank());
    }

    @Test
    void all_samples_immutable() {
        assertThatThrownBy(() -> TesseraFixtures.ITEM_IDS.add("pumpkin"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> TesseraFixtures.TOOLTIP_TEXT_SAMPLES.add("foo"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> TesseraFixtures.ENCHANTMENT_COMBOS.add(Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        // entries inside combos must themselves be immutable
        assertThatThrownBy(() -> TesseraFixtures.ENCHANTMENT_COMBOS.get(0).put("foo", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
