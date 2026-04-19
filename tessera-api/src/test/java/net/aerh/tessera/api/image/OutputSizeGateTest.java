package net.aerh.tessera.api.image;

import net.aerh.tessera.api.exception.OutputTooLargeException;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OutputSizeGate}   / .
 *
 * <p>{@code checkOrThrow} must throw {@link OutputTooLargeException} strictly when
 * {@code bytes.length > capBytes} and pass through (return unchanged) otherwise.
 * {@code resolveStaticCap} / {@code resolveAnimatedCap} precedence is builder &gt; env
 * &gt; default.
 *
 * <p>Relocated from tessera-core to tessera-api when OutputSizeGate itself moved
 * into the api module so {@code GeneratorResult.StaticImage.toBytes} /
 * {@code AnimatedImage.toWebpBytes} can invoke the gate without forcing an
 * api->core dependency.
 */
final class OutputSizeGateTest {

    private static final long ONE_MB = 1024L * 1024L;

    // -------------------------------------------------------------------------
    // checkOrThrow
    // -------------------------------------------------------------------------

    @Test
    void checkOrThrow_within_cap_returns_bytes() {
        byte[] input = new byte[1024];
        byte[] returned = OutputSizeGate.checkOrThrow(input, 8L * ONE_MB, "item");
        assertThat(returned).isSameAs(input);
    }

    @Test
    void checkOrThrow_exactly_at_cap_returns_bytes() {
        byte[] input = new byte[(int) ONE_MB];
        byte[] returned = OutputSizeGate.checkOrThrow(input, ONE_MB, "item");
        assertThat(returned).isSameAs(input);
    }

    @Test
    void checkOrThrow_over_cap_throws() {
        byte[] input = new byte[1024 + 1];
        assertThatThrownBy(() -> OutputSizeGate.checkOrThrow(input, 1024L, "animated-gif"))
                .isInstanceOf(OutputTooLargeException.class);
    }

    @Test
    void checkOrThrow_null_bytes_throws_npe() {
        assertThatThrownBy(() -> OutputSizeGate.checkOrThrow(null, 1024L, "item"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void checkOrThrow_null_render_type_throws_npe() {
        byte[] input = new byte[]{1};
        assertThatThrownBy(() -> OutputSizeGate.checkOrThrow(input, 1024L, null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------
    // resolveStaticCap
    // -------------------------------------------------------------------------

    @Test
    @ClearEnvironmentVariable(key = "TESSERA_STATIC_OUTPUT_CAP_BYTES")
    void resolveStaticCap_no_env_no_override_returns_default() {
        assertThat(OutputSizeGate.resolveStaticCap(null)).isEqualTo(OutputSizeGate.DEFAULT_STATIC_CAP);
    }

    @Test
    @SetEnvironmentVariable(key = "TESSERA_STATIC_OUTPUT_CAP_BYTES", value = "2000")
    void resolveStaticCap_env_set_no_override_returns_env() {
        assertThat(OutputSizeGate.resolveStaticCap(null)).isEqualTo(2000L);
    }

    @Test
    @SetEnvironmentVariable(key = "TESSERA_STATIC_OUTPUT_CAP_BYTES", value = "1000")
    void resolveStaticCap_builder_override_wins_over_env() {
        assertThat(OutputSizeGate.resolveStaticCap(2000L)).isEqualTo(2000L);
    }

    @Test
    @SetEnvironmentVariable(key = "TESSERA_STATIC_OUTPUT_CAP_BYTES", value = "not-a-number")
    void resolveStaticCap_malformed_env_falls_back_to_default() {
        assertThat(OutputSizeGate.resolveStaticCap(null)).isEqualTo(OutputSizeGate.DEFAULT_STATIC_CAP);
    }

    // -------------------------------------------------------------------------
    // resolveAnimatedCap
    // -------------------------------------------------------------------------

    @Test
    @ClearEnvironmentVariable(key = "TESSERA_ANIMATED_OUTPUT_CAP_BYTES")
    void resolveAnimatedCap_no_env_no_override_returns_default() {
        assertThat(OutputSizeGate.resolveAnimatedCap(null)).isEqualTo(OutputSizeGate.DEFAULT_ANIMATED_CAP);
    }

    @Test
    @SetEnvironmentVariable(key = "TESSERA_ANIMATED_OUTPUT_CAP_BYTES", value = "5000")
    void resolveAnimatedCap_env_set_returns_env() {
        assertThat(OutputSizeGate.resolveAnimatedCap(null)).isEqualTo(5000L);
    }

    @Test
    @SetEnvironmentVariable(key = "TESSERA_ANIMATED_OUTPUT_CAP_BYTES", value = "5000")
    void resolveAnimatedCap_builder_override_wins_over_env() {
        assertThat(OutputSizeGate.resolveAnimatedCap(9999L)).isEqualTo(9999L);
    }

    // -------------------------------------------------------------------------
    // Defaults match table
    // -------------------------------------------------------------------------

    @Test
    void default_static_cap_is_8_megabytes() {
        assertThat(OutputSizeGate.DEFAULT_STATIC_CAP).isEqualTo(8L * 1024 * 1024);
    }

    @Test
    void default_animated_cap_is_24_megabytes() {
        assertThat(OutputSizeGate.DEFAULT_ANIMATED_CAP).isEqualTo(24L * 1024 * 1024);
    }
}
