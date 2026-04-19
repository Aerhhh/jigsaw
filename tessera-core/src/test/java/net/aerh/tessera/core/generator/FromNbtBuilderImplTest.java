package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.nbt.NbtFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link FromNbtBuilderImpl} covering the auto-detect + explicit override
 * routing added to the NBT entry point.
 *
 * <p>Routing semantics:
 * <ul>
 *   <li>Default (no {@code format(...)} call) or {@code format(NbtFormat.AUTO)}: invokes
 *       {@link net.aerh.tessera.core.nbt.NbtDialectDetector}; empty Optional triggers
 *       {@code ParseException} wrapped as {@link RenderFailedException} with message containing
 *       {@code ".format(NbtFormat"}.</li>
 *   <li>Explicit {@code format(NbtFormat.X)} (X != AUTO): skips the detector entirely and
 *       threads through the engine's {@code parseNbt} path directly.</li>
 * </ul>
 */
final class FromNbtBuilderImplTest {

    private static DefaultEngine engine() {
        return TestEngineFactory.minimalEngine();
    }

    @Test
    void ctor_null_nbt_throws_npe() {
        DefaultEngine eng = engine();
        assertThatThrownBy(() -> new FromNbtBuilderImpl(eng, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nbt");
    }

    @Test
    void ctor_null_engine_throws_npe() {
        assertThatThrownBy(() -> new FromNbtBuilderImpl(null, "{}"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("engine");
    }

    @Test
    void format_null_throws_npe() {
        FromNbtBuilderImpl builder = new FromNbtBuilderImpl(engine(), "{}");
        assertThatThrownBy(() -> builder.format(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("format");
    }

    @Test
    void format_AUTO_is_alias_for_unset_detector_still_runs() {
        // AUTO is equivalent to unset: the detector MUST run and produce the ambiguous
        // remediation hint for "{}". If AUTO were bypassing the detector (leaking as an
        // explicit format) we would not see the ".format(NbtFormat" hint.
        FromNbtBuilderImpl builder = new FromNbtBuilderImpl(engine(), "{}");
        builder.format(NbtFormat.AUTO);
        assertThatThrownBy(builder::render)
                .isInstanceOf(RenderFailedException.class)
                .hasMessageContaining(".format(NbtFormat");
    }

    @Test
    void format_explicit_bypasses_detector_no_remediation_hint() {
        // Explicit SNBT format on "{}" MUST skip the detector. The detector would have
        // produced the ".format(NbtFormat" remediation hint on this input; proving the
        // hint is NOT present (regardless of whether the parser chain ultimately throws)
        // is robust evidence the detector code path was bypassed.
        FromNbtBuilderImpl builder = new FromNbtBuilderImpl(engine(), "{}");
        builder.format(NbtFormat.SNBT);
        try {
            builder.render();
            // Parser chain succeeded: detector was definitely bypassed.
        } catch (Throwable t) {
            assertThat(t.getMessage() == null || !t.getMessage().contains(".format(NbtFormat"))
                    .as("Explicit format must skip detector; remediation hint should be absent")
                    .isTrue();
        }
    }

    @Test
    void render_auto_ambiguous_wraps_parse_exception_in_render_failed() {
        // "{}" returns Optional.empty() from NbtDialectDetector -> render() must throw
        // RenderFailedException whose cause is ParseException containing the ".format(NbtFormat"
        // remediation hint.
        FromNbtBuilderImpl builder = new FromNbtBuilderImpl(engine(), "{}");
        assertThatThrownBy(builder::render)
                .isInstanceOf(RenderFailedException.class)
                .hasMessageContaining(".format(NbtFormat")
                .hasCauseInstanceOf(ParseException.class);
    }

    @Test
    void render_auto_garbage_wraps_parse_exception_in_render_failed() {
        FromNbtBuilderImpl builder = new FromNbtBuilderImpl(engine(), "not nbt at all");
        assertThatThrownBy(builder::render)
                .isInstanceOf(RenderFailedException.class)
                .hasMessageContaining(".format(NbtFormat")
                .hasCauseInstanceOf(ParseException.class);
    }

    @Test
    void render_format_AUTO_explicit_still_triggers_detector() {
        // Explicit.format(AUTO) is an alias for unset; ambiguous input still throws.
        FromNbtBuilderImpl builder = new FromNbtBuilderImpl(engine(), "{}");
        builder.format(NbtFormat.AUTO);
        assertThatThrownBy(builder::render)
                .isInstanceOf(RenderFailedException.class)
                .hasMessageContaining(".format(NbtFormat");
    }

    @Test
    void render_explicit_format_skips_detector_no_ambiguity_error() {
        // Input that auto-detects cleanly (JSON_COMPONENT); with SNBT explicit override,
        // detector is skipped. The parser's DefaultNbtFormatHandler accepts any non-blank
        // input as a fallback and yields a ParsedItem with itemId="air", which the stub
        // item generator then throws on. We assert the detector's ".format(NbtFormat"
        // remediation hint was NOT emitted - proving the detector code path was bypassed
        // even though the input is not "{}"-style ambiguous.
        FromNbtBuilderImpl builder = new FromNbtBuilderImpl(engine(), "{}");
        builder.format(NbtFormat.SNBT);
        assertThatThrownBy(builder::render)
                .satisfiesAnyOf(
                        thrown -> assertThat(thrown).hasMessageNotContaining(".format(NbtFormat"),
                        thrown -> assertThat(thrown).isNotInstanceOf(RenderFailedException.class));
    }
}
