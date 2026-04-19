package net.aerh.tessera.testkit.image;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD RED-first specification for {@link GoldenImageAssertion}.
 *
 * <p>Covers the two-gate assertion composition (SSIM floor first, deltaE hotspot second),
 * the diff-artifact write-out on failure, and the overload delegation to
 * {@link GoldenImageConfig#GLOBAL_SSIM_FLOOR} and {@link GoldenImageConfig#MAX_HOTSPOT_PCT}.
 */
class GoldenImageAssertionTest {

    @Test
    void identical_images_pass(@TempDir Path diffDir) {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        try {
            BufferedImage img = solidColor(128, 128, Color.WHITE);
            assertThatCode(() -> GoldenImageAssertion.assertGoldenEquals(img, img))
                    .doesNotThrowAnyException();
        } finally {
            System.clearProperty("tessera.golden.diffDir");
        }
    }

    @Test
    void ssim_below_floor_throws_with_diff_written(@TempDir Path diffDir) {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        try {
            BufferedImage baseline = solidColor(128, 128, new Color(128, 128, 128));
            BufferedImage mutated = copyOf(baseline);
            Random rng = new Random(1L);
            int total = 128 * 128;
            int target = total / 4;
            for (int i = 0; i < target; i++) {
                int x = rng.nextInt(128);
                int y = rng.nextInt(128);
                int rgb = 0xFF000000 | (rng.nextInt(0xFFFFFF));
                mutated.setRGB(x, y, rgb);
            }

            assertThatThrownBy(() -> GoldenImageAssertion.assertGoldenEquals(mutated, baseline))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("SSIM");

            assertThat(diffDir.resolve("actual.png")).exists();
            assertThat(diffDir.resolve("expected.png")).exists();
            assertThat(diffDir.resolve("diff.png")).exists();
        } finally {
            System.clearProperty("tessera.golden.diffDir");
        }
    }

    @Test
    void hotspot_above_limit_throws(@TempDir Path diffDir) {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        try {
            // 512x512 all-white baseline; mutated copy has a 40x40 pure-red patch.
            // Patch occupies 1600 pixels (~0.61% of 262144), keeping SSIM above the 0.995
            // floor (SSIM is window-averaged and the patch hits only a small fraction of
            // the 8x8 windows) while pushing the deltaE hotspot fraction above the 0.5%
            // limit so the deltaE gate fires first rather than the SSIM gate.
            BufferedImage baseline = solidColor(512, 512, Color.WHITE);
            BufferedImage mutated = copyOf(baseline);
            Graphics2D g = mutated.createGraphics();
            try {
                g.setColor(Color.RED);
                g.fillRect(0, 0, 40, 40);
            } finally {
                g.dispose();
            }

            assertThatThrownBy(() -> GoldenImageAssertion.assertGoldenEquals(mutated, baseline))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("deltaE");
        } finally {
            System.clearProperty("tessera.golden.diffDir");
        }
    }

    @Test
    void null_args_npe() {
        BufferedImage img = solidColor(8, 8, Color.WHITE);
        assertThatNullPointerException().isThrownBy(
                () -> GoldenImageAssertion.assertGoldenEquals(null, img));
        assertThatNullPointerException().isThrownBy(
                () -> GoldenImageAssertion.assertGoldenEquals(img, null));
    }

    @Test
    void default_overload_uses_global_floor_and_hotspot_limit(@TempDir Path diffDir) {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        try {
            BufferedImage img = solidColor(64, 64, Color.BLUE);
            // Identical inputs trivially pass both gates; the overload wiring is what's under test.
            assertThatCode(() -> GoldenImageAssertion.assertGoldenEquals(img, img))
                    .doesNotThrowAnyException();
            // Now verify the explicit 4-arg form using the constants accepts the same input.
            assertThatCode(() -> GoldenImageAssertion.assertGoldenEquals(
                    img, img,
                    GoldenImageConfig.GLOBAL_SSIM_FLOOR,
                    GoldenImageConfig.MAX_HOTSPOT_PCT))
                    .doesNotThrowAnyException();
        } finally {
            System.clearProperty("tessera.golden.diffDir");
        }
    }

    // -----------------------------------------------------------------------
    // Per-fixture SSIM logging contract.
    //
    // The 5-arg overload is the canonical entry point; GoldenFixturesTest routes
    // through it so the calibration pipeline can grep per-fixture SSIM from
    // surefire stdout. Format is locked: `SSIM for <baselineId>: <ssimDouble>`
    // with a single space after "for" and a single space after ":".
    //
    // The 4-arg overload stays for back-compat and synthesises a dims-based id;
    // external consumers that call it through testkit do not regress.
    // -----------------------------------------------------------------------

    @Test
    void assertGoldenEquals_emits_ssim_log_line_on_pass(@TempDir Path diffDir) {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            BufferedImage img = solidColor(16, 16, Color.WHITE);
            GoldenImageAssertion.assertGoldenEquals(img, img, 0.99, 0.01, "unit/identity");

            // Identical images yield SSIM = 1.0; log line must appear even though the
            // assertion passes. Format pinned: `SSIM for <id>: <value>`.
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.matches("SSIM for unit/identity: 1(\\.0+)?"));
        } finally {
            detachAppender(appender);
            System.clearProperty("tessera.golden.diffDir");
        }
    }

    @Test
    void assertGoldenEquals_emits_ssim_log_line_before_failure(@TempDir Path diffDir) {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            // Forced SSIM failure: all-white vs all-black, SSIM near zero.
            BufferedImage baseline = solidColor(32, 32, Color.WHITE);
            BufferedImage mutated = solidColor(32, 32, Color.BLACK);

            assertThatThrownBy(() -> GoldenImageAssertion.assertGoldenEquals(
                    mutated, baseline, 0.99, 0.01, "unit/failure"))
                    .isInstanceOf(AssertionError.class);

            // Critical: the log line must fire on the failure path too, so
            // calibrators can see the actual SSIM score that tripped the floor.
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.startsWith("SSIM for unit/failure: "));
        } finally {
            detachAppender(appender);
            System.clearProperty("tessera.golden.diffDir");
        }
    }

    @Test
    void assertGoldenEquals_four_arg_delegates_with_synthetic_id(@TempDir Path diffDir) {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            BufferedImage img = solidColor(7, 3, Color.WHITE);
            GoldenImageAssertion.assertGoldenEquals(img, img, 0.99, 0.01);

            // 4-arg form must still emit the log line; the synthetic id is
            // `<w>x<h>-unnamed` so back-compat callers remain greppable (albeit
            // without a meaningful correlation to any fixture).
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.matches("SSIM for 7x3-unnamed: 1(\\.0+)?"));
        } finally {
            detachAppender(appender);
            System.clearProperty("tessera.golden.diffDir");
        }
    }

    @Test
    void assertGoldenEquals_null_baselineId_npe() {
        BufferedImage img = solidColor(8, 8, Color.WHITE);
        assertThatNullPointerException().isThrownBy(() ->
                GoldenImageAssertion.assertGoldenEquals(img, img, 0.99, 0.01, null));
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(GoldenImageAssertion.class);
        // Force INFO-level capture even if a test runner has temporarily bumped the
        // level; the production logback-test.xml configures this logger at INFO but
        // we set it explicitly here to be robust against test-order-dependent edits.
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(GoldenImageAssertion.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void diff_png_dimensions_match_input(@TempDir Path diffDir) throws Exception {
        System.setProperty("tessera.golden.diffDir", diffDir.toString());
        try {
            BufferedImage baseline = solidColor(96, 48, Color.WHITE);
            BufferedImage mutated = solidColor(96, 48, Color.BLACK); // force failure

            assertThatThrownBy(() -> GoldenImageAssertion.assertGoldenEquals(mutated, baseline))
                    .isInstanceOf(AssertionError.class);

            BufferedImage diff = javax.imageio.ImageIO.read(diffDir.resolve("diff.png").toFile());
            assertThat(diff.getWidth()).isEqualTo(96);
            assertThat(diff.getHeight()).isEqualTo(48);
        } finally {
            System.clearProperty("tessera.golden.diffDir");
        }
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

    private static BufferedImage copyOf(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }
}
