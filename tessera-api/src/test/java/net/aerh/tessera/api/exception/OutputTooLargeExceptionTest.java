package net.aerh.tessera.api.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OutputTooLargeException} shape + diagnostic context.
 */
final class OutputTooLargeExceptionTest {

    private static final long ONE_MEGABYTE = 1L * 1024 * 1024;
    private static final long EIGHT_MEGABYTES = 8L * 1024 * 1024;

    @Test
    void message_contains_size_cap_rendertype() {
        OutputTooLargeException ex = new OutputTooLargeException(ONE_MEGABYTE, EIGHT_MEGABYTES, "item");

        assertThat(ex.getMessage()).contains(String.valueOf(ONE_MEGABYTE));
        assertThat(ex.getMessage()).contains(String.valueOf(EIGHT_MEGABYTES));
        assertThat(ex.getMessage()).contains("item");
    }

    @Test
    void context_map_has_all_fields() {
        OutputTooLargeException ex = new OutputTooLargeException(ONE_MEGABYTE, EIGHT_MEGABYTES, "tooltip");

        assertThat(ex.getContext())
                .containsEntry("actualBytes", ONE_MEGABYTE)
                .containsEntry("capBytes", EIGHT_MEGABYTES)
                .containsEntry("renderType", "tooltip");
    }

    @Test
    void inherits_tessera_exception() {
        OutputTooLargeException ex = new OutputTooLargeException(1, 1, "test");
        TesseraException upcast = ex;
        assertThat(upcast).isInstanceOf(RuntimeException.class);
    }

    @Test
    void getters_return_constructor_values() {
        OutputTooLargeException ex = new OutputTooLargeException(42L, 999L, "animated-webp");
        assertThat(ex.actualBytes()).isEqualTo(42L);
        assertThat(ex.capBytes()).isEqualTo(999L);
        assertThat(ex.renderType()).isEqualTo("animated-webp");
    }

    @Test
    void null_render_type_throws_npe() {
        assertThatThrownBy(() -> new OutputTooLargeException(1, 2, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("renderType");
    }
}
