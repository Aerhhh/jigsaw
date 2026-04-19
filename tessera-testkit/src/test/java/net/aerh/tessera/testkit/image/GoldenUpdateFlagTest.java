package net.aerh.tessera.testkit.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD RED-first specification for the {@code -Dtessera.golden.update=true} flag guard.
 * Must refuse to regenerate when running under CI ({@code CI=true} env) so committed
 * baselines cannot be silently overwritten from a PR build.
 */
class GoldenUpdateFlagTest {

    @Test
    @SetSystemProperty(key = "tessera.golden.update", value = "true")
    @SetEnvironmentVariable(key = "CI", value = "") // ensure not-CI for this test
    void update_flag_set_regenerates_baseline(@TempDir Path dir) throws Exception {
        BufferedImage img = solidColor(16, 16, Color.GREEN);
        Path target = dir.resolve("nested").resolve("baseline.png");

        boolean regenerated = GoldenImageAssertion.updateIfRequested(img, target);

        assertThat(regenerated).isTrue();
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.size(target)).isPositive();
    }

    @Test
    @SetSystemProperty(key = "tessera.golden.update", value = "true")
    @SetEnvironmentVariable(key = "CI", value = "true")
    void ci_env_refuses_update_even_with_flag(@TempDir Path dir) {
        BufferedImage img = solidColor(16, 16, Color.GREEN);
        Path target = dir.resolve("baseline.png");

        assertThatThrownBy(() -> GoldenImageAssertion.updateIfRequested(img, target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CI");
        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    @SetEnvironmentVariable(key = "CI", value = "")
    void flag_unset_returns_false_no_write(@TempDir Path dir) {
        // intentionally no @SetSystemProperty: the flag is absent
        BufferedImage img = solidColor(16, 16, Color.GREEN);
        Path target = dir.resolve("baseline.png");

        boolean regenerated = GoldenImageAssertion.updateIfRequested(img, target);

        assertThat(regenerated).isFalse();
        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    @SetEnvironmentVariable(key = "CI", value = "true")
    void regenerateBaseline_ci_refusal_direct(@TempDir Path dir) {
        BufferedImage img = solidColor(16, 16, Color.GREEN);
        Path target = dir.resolve("baseline.png");

        assertThatThrownBy(() -> GoldenImageAssertion.regenerateBaseline(img, target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CI");
    }

    private static BufferedImage solidColor(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }
}
