package net.aerh.imagegenerator.parser.inventory;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.InventoryItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryStringParserTest {

    private final InventoryStringParser parser = new InventoryStringParser(54);

    // Simple format: material:slot,amount

    @Test
    void parseSimpleItem() {
        ArrayList<InventoryItem> items = parser.parse("stone:1,64");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getItemName()).isEqualTo("stone");
        assertThat(item.getSlot()).containsExactly(1);
        assertThat(item.getAmount()).containsExactly(64);
    }

    @Test
    void parseSimpleItemDefaultAmount() {
        ArrayList<InventoryItem> items = parser.parse("diamond:5");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getItemName()).isEqualTo("diamond");
        assertThat(item.getSlot()).containsExactly(5);
        assertThat(item.getAmount()).containsExactly(1);
    }

    @Test
    void parseNamespacedItem() {
        ArrayList<InventoryItem> items = parser.parse("minecraft:diamond_sword:1,1");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getItemName()).isEqualTo("minecraft:diamond_sword");
    }

    // Array format: material:[slot1,slot2,...],amount

    @Test
    void parseArraySlots() {
        ArrayList<InventoryItem> items = parser.parse("stone:[1,2,3],32");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getItemName()).isEqualTo("stone");
        assertThat(item.getSlot()).containsExactly(1, 2, 3);
        assertThat(item.getAmount()).containsExactly(32, 32, 32);
    }

    @Test
    void parseArraySlotRange() {
        ArrayList<InventoryItem> items = parser.parse("stone:[1-4],16");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getSlot()).containsExactly(1, 2, 3, 4);
        assertThat(item.getAmount()).containsExactly(16, 16, 16, 16);
    }

    @Test
    void parseArrayDefaultAmount() {
        ArrayList<InventoryItem> items = parser.parse("stone:[1,2]");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getSlot()).containsExactly(1, 2);
        assertThat(item.getAmount()).containsExactly(1, 1);
    }

    // Map format: material:{slot:amount,slot:amount}

    @Test
    void parseMapSlots() {
        ArrayList<InventoryItem> items = parser.parse("stone:{1:64,2:32}");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getItemName()).isEqualTo("stone");
        assertThat(item.getSlot()).containsExactly(1, 2);
        assertThat(item.getAmount()).containsExactly(64, 32);
    }

    // Modifiers

    @Test
    void parseWithModifier() {
        ArrayList<InventoryItem> items = parser.parse("leather_chestplate,red:1,1");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getItemName()).isEqualTo("leather_chestplate");
        assertThat(item.getExtraContent()).isEqualTo("red");
    }

    @Test
    void parseWithDurability() {
        ArrayList<InventoryItem> items = parser.parse("diamond_pickaxe,50:1,1");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getItemName()).isEqualTo("diamond_pickaxe");
        assertThat(item.getDurabilityPercent()).isEqualTo(50);
        assertThat(item.getExtraContent()).isNull();
    }

    @Test
    void parseWithModifierAndDurability() {
        ArrayList<InventoryItem> items = parser.parse("diamond_sword,enchant,75:1,1");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getItemName()).isEqualTo("diamond_sword");
        assertThat(item.getExtraContent()).isEqualTo("enchant");
        assertThat(item.getDurabilityPercent()).isEqualTo(75);
    }

    // Multiple items

    @Test
    void parseMultipleItems() {
        ArrayList<InventoryItem> items = parser.parse("stone:1,64%% diamond:2,32");
        assertThat(items).hasSize(2);

        assertThat(items.get(0).getItemName()).isEqualTo("stone");
        assertThat(items.get(0).getSlot()).containsExactly(1);
        assertThat(items.get(0).getAmount()).containsExactly(64);

        assertThat(items.get(1).getItemName()).isEqualTo("diamond");
        assertThat(items.get(1).getSlot()).containsExactly(2);
        assertThat(items.get(1).getAmount()).containsExactly(32);
    }

    // Slot conflict resolution

    @Test
    void laterItemOverridesEarlierForSameSlot() {
        ArrayList<InventoryItem> items = parser.parse("stone:1,64%% diamond:1,32");
        assertThat(items).hasSize(2);

        // First item has its slot removed due to conflict
        assertThat(items.get(0).getSlot()).isEmpty();
        // Second item keeps the slot
        assertThat(items.get(1).getItemName()).isEqualTo("diamond");
        assertThat(items.get(1).getSlot()).containsExactly(1);
    }

    // Edge cases

    @Test
    void amountClampedTo64() {
        ArrayList<InventoryItem> items = parser.parse("stone:1,999");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getAmount()).containsExactly(64);
    }

    @Test
    void slotClampedToTotal() {
        InventoryStringParser smallParser = new InventoryStringParser(9);
        ArrayList<InventoryItem> items = smallParser.parse("stone:100,1");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getSlot()).containsExactly(9);
    }

    @Test
    void durabilityClampedTo0Through100() {
        ArrayList<InventoryItem> items = parser.parse("stone,150:1,1");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getDurabilityPercent()).isEqualTo(100);
    }

    // Error cases

    @Test
    void missingSlotSeparatorThrows() {
        assertThatThrownBy(() -> parser.parse("stone"))
            .isInstanceOf(GeneratorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stone:{abc:64}", "stone:{1:abc}"})
    void invalidMapFormatThrows(String input) {
        assertThatThrownBy(() -> parser.parse(input))
            .isInstanceOf(Exception.class);
    }

    @Test
    void invalidSlotThrows() {
        assertThatThrownBy(() -> parser.parse("stone:abc"))
            .isInstanceOf(GeneratorException.class);
    }

    @Test
    void mapSlotRangeExpansion() {
        ArrayList<InventoryItem> items = parser.parse("stone:{1-3:64}");
        assertThat(items).hasSize(1);

        InventoryItem item = items.getFirst();
        assertThat(item.getSlot()).containsExactly(1, 2, 3);
        assertThat(item.getAmount()).containsExactly(64, 64, 64);
    }
}
