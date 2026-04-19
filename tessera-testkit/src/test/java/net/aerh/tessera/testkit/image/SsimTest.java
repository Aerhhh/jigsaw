package net.aerh.tessera.testkit.image;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * TDD RED-first specification for {@link Ssim}.
 *
 * <p>Asserts known mathematical properties of SSIM so a mutated impl (e.g. a
 * constant-return-1.0 implementation that would silently pass every golden) fails
 * several tests at once.
 */
class SsimTest {

    @Test
    void identical_images_ssim_equals_1() {
        BufferedImage img = solidColor(64, 64, new Color(120, 45, 210));
        double ssim = Ssim.compare(img, img);
        assertThat(ssim).isGreaterThanOrEqualTo(0.9999);
    }

    @Test
    void pure_white_vs_pure_black_ssim_near_zero() {
        BufferedImage white = solidColor(64, 64, Color.WHITE);
        BufferedImage black = solidColor(64, 64, Color.BLACK);
        double ssim = Ssim.compare(white, black);
        assertThat(ssim).isLessThan(0.05);
    }

    @Test
    void one_pixel_mutation_ssim_above_global_floor() {
        BufferedImage baseline = solidColor(256, 256, new Color(128, 128, 128));
        BufferedImage mutated = copyOf(baseline);
        mutated.setRGB(100, 100, 0xFFFF0000);
        double ssim = Ssim.compare(baseline, mutated);
        assertThat(ssim).isGreaterThan(0.995);
    }

    @Test
    void ten_percent_noise_ssim_below_tooltip_threshold() {
        BufferedImage baseline = solidColor(128, 128, new Color(128, 128, 128));
        BufferedImage noisy = copyOf(baseline);
        Random rng = new Random(42L);
        int total = 128 * 128;
        int target = total / 10;
        for (int i = 0; i < target; i++) {
            int x = rng.nextInt(128);
            int y = rng.nextInt(128);
            int rgb = 0xFF000000 | (rng.nextInt(0xFFFFFF));
            noisy.setRGB(x, y, rgb);
        }
        double ssim = Ssim.compare(baseline, noisy);
        assertThat(ssim).isLessThan(0.98);
    }

    @Test
    void null_arg_throws_npe() {
        BufferedImage img = solidColor(8, 8, Color.WHITE);
        assertThatNullPointerException().isThrownBy(() -> Ssim.compare(null, img));
        assertThatNullPointerException().isThrownBy(() -> Ssim.compare(img, null));
    }

    @Test
    void mismatched_dimensions_throws_iae() {
        BufferedImage a = solidColor(64, 64, Color.WHITE);
        BufferedImage b = solidColor(32, 32, Color.WHITE);
        assertThatIllegalArgumentException().isThrownBy(() -> Ssim.compare(a, b));
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
