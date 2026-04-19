package net.aerh.tessera.core.generator;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  selection-overlay plumbing tests. The InventoryRequest record carries a
 * {@code selectedSlots} set; the field round-trips through the builder and survives
 * the constructor validation pipeline.
 *
 * <p>Pixel-level validation of the selection overlay lives in the golden-fixtures
 * suite ({@link GoldenFixturesTest} inventory/slot-selected scenario), which is
 * env-gated on {@code TESSERA_ASSETS_AVAILABLE=true}.
 */
class InventorySelectionTest {

    @Test
    void inventoryRequest_defaults_selectedSlots_to_empty_set() {
        InventoryRequest req = InventoryRequest.builder()
                .rows(1).slotsPerRow(9).build();
        assertThat(req.selectedSlots()).isEmpty();
    }

    @Test
    void inventoryRequest_preserves_selectedSlots_through_builder() {
        InventoryRequest req = InventoryRequest.builder()
                .rows(1).slotsPerRow(9)
                .selectedSlots(Set.of(0, 4, 8))
                .build();
        assertThat(req.selectedSlots()).containsExactlyInAnyOrder(0, 4, 8);
    }

    @Test
    void inventoryRequest_selectedSlots_is_unmodifiable() {
        InventoryRequest req = InventoryRequest.builder()
                .rows(1).slotsPerRow(9)
                .selectedSlots(Set.of(3))
                .build();
        assertThatThrownBy(() -> req.selectedSlots().add(4))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void inventoryRequest_null_selectedSlots_is_rejected() {
        assertThatThrownBy(() -> InventoryRequest.builder()
                .rows(1).slotsPerRow(9)
                .selectedSlots(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void inventoryRequest_selectedSlot_convenience_appends() {
        InventoryRequest req = InventoryRequest.builder()
                .rows(1).slotsPerRow(9)
                .selectedSlot(2)
                .selectedSlot(5)
                .build();
        assertThat(req.selectedSlots()).containsExactlyInAnyOrder(2, 5);
    }

    @Test
    void inventoryRequest_selectedSlots_factor_into_cacheKey() {
        InventoryRequest a = InventoryRequest.builder()
                .rows(1).slotsPerRow(9).selectedSlot(0).build();
        InventoryRequest b = InventoryRequest.builder()
                .rows(1).slotsPerRow(9).selectedSlot(1).build();
        assertThat(a.cacheKey()).isNotEqualTo(b.cacheKey());
    }
}
