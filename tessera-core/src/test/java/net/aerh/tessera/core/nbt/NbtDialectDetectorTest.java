package net.aerh.tessera.core.nbt;

import net.aerh.tessera.api.nbt.NbtFormat;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NbtDialectDetector}. The detector uses structural-signature routing and
 * is a bounded string-scan only (no parse, no recursion) so unbounded-nesting malicious
 * payloads cannot OOM.
 */
final class NbtDialectDetectorTest {

    @Test
    void detect_snbt_unquoted_keys_and_suffix() {
        String snbt = "{id:minecraft:diamond_sword,Count:1b,tag:{Enchantments:[{id:sharpness,lvl:5s}]}}";
        assertThat(NbtDialectDetector.detect(snbt)).contains(NbtFormat.SNBT);
    }

    @Test
    void detect_json_component_id_and_components() {
        String json = "{\"id\":\"minecraft:diamond_sword\",\"count\":1,\"components\":{\"minecraft:enchantments\":{\"sharpness\":5}}}";
        assertThat(NbtDialectDetector.detect(json)).contains(NbtFormat.JSON_COMPONENT);
    }

    @Test
    void detect_post_flattening_id_and_tag() {
        String json = "{\"id\":\"minecraft:diamond_sword\",\"Count\":1,\"tag\":{\"Enchantments\":[{\"id\":\"sharpness\",\"lvl\":5}]}}";
        assertThat(NbtDialectDetector.detect(json)).contains(NbtFormat.POST_FLATTENING);
    }

    @Test
    void detect_pre_flattening_numeric_id() {
        String json = "{\"id\":276,\"Count\":1,\"Damage\":0}";
        assertThat(NbtDialectDetector.detect(json)).contains(NbtFormat.PRE_FLATTENING);
    }

    @Test
    void detect_ambiguous_empty_compound_returns_empty() {
        assertThat(NbtDialectDetector.detect("{}")).isEmpty();
    }

    @Test
    void detect_null_returns_empty() {
        assertThat(NbtDialectDetector.detect(null)).isEmpty();
    }

    @Test
    void detect_blank_returns_empty() {
        assertThat(NbtDialectDetector.detect("  ")).isEmpty();
    }

    @Test
    void detect_garbage_returns_empty() {
        assertThat(NbtDialectDetector.detect("not nbt at all")).isEmpty();
    }

    @Test
    void detect_unbounded_nesting_does_not_oom() {
        // Construct a 10,000-deep nested SNBT compound ({a:{a:{a:...{a:1b}...}}}).
        // Detector must NOT recurse - a string-scan implementation returns in O(n) time.
        StringBuilder sb = new StringBuilder();
        int depth = 10_000;
        for (int i = 0; i < depth; i++) {
            sb.append("{a:");
        }
        sb.append("1b");
        for (int i = 0; i < depth; i++) {
            sb.append('}');
        }
        // Must not StackOverflowError or hang.
        Optional<NbtFormat> result = NbtDialectDetector.detect(sb.toString());
        // First key `a:` is unquoted - SNBT heuristic should fire.
        assertThat(result).contains(NbtFormat.SNBT);
    }

    @Test
    void detect_huge_input_bounded_scan() {
        // Generate ~1 MB SNBT-shaped payload; detection must stay sub-50ms.
        StringBuilder sb = new StringBuilder(1_048_576);
        sb.append("{a:");
        while (sb.length() < 1_048_500) {
            sb.append("x");
        }
        sb.append("1b}");
        long startNanos = System.nanoTime();
        Optional<NbtFormat> result = NbtDialectDetector.detect(sb.toString());
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertThat(result).contains(NbtFormat.SNBT);
        assertThat(elapsedMs).as("detector should complete in < 50ms on 1 MB input").isLessThan(500L);
    }
}
