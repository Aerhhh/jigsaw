package net.aerh.tessera.testkit.image;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * TDD RED-first specification for {@link DeltaE}.
 *
 * <p>Tests both the Lab conversion (white-point sanity) and the public pixels-over-threshold
 * gate that {@link GoldenImageAssertion} composes with SSIM.
 */
class DeltaETest {

    @Test
    void identical_images_zero_hotspot() {
        BufferedImage img = solidColor(64, 64, new Color(200, 50, 30));
        double fraction = DeltaE.pixelsOverThreshold(img, img, 5.0);
        assertThat(fraction).isEqualTo(0.0);
    }

    @Test
    void single_red_pixel_hotspot() {
        BufferedImage baseline = solidColor(128, 128, Color.WHITE);
        BufferedImage mutated = copyOf(baseline);
        mutated.setRGB(0, 0, 0xFFFF0000);
        double fraction = DeltaE.pixelsOverThreshold(baseline, mutated, 5.0);
        assertThat(fraction).isEqualTo(1.0 / (128.0 * 128.0), within(1e-9));
    }

    @Test
    void all_pixels_different_full_hotspot() {
        BufferedImage white = solidColor(32, 32, Color.WHITE);
        BufferedImage black = solidColor(32, 32, Color.BLACK);
        double fraction = DeltaE.pixelsOverThreshold(white, black, 5.0);
        assertThat(fraction).isEqualTo(1.0);
    }

    @Test
    void threshold_boundary_strict_greater_than() {
        // Two pixels with a small but non-zero delta; pick a delta <= 5 so neither is counted.
        BufferedImage a = solidColor(4, 4, new Color(120, 120, 120));
        BufferedImage b = solidColor(4, 4, new Color(121, 120, 120));
        double fraction = DeltaE.pixelsOverThreshold(a, b, 5.0);
        // A one-unit sRGB step near mid-grey has deltaE76 well below 5; boundary value exactly
        // 5.0 would not be counted, the strict-greater-than gate drops anything at or below.
        assertThat(fraction).isEqualTo(0.0);
    }

    @Test
    void rgb_to_lab_white_point() {
        double[] lab = DeltaE.rgbToLab(255, 255, 255);
        assertThat(lab[0]).isCloseTo(100.0, within(1.0));
        assertThat(lab[1]).isCloseTo(0.0, within(1.0));
        assertThat(lab[2]).isCloseTo(0.0, within(1.0));
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
