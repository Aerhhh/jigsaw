package net.aerh.tessera.image;

import net.aerh.tessera.core.image.ImageOps;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ImageOps}. Covers five locked behaviours: upscale correctness
 * (nearest-neighbor, not interpolation), scale=1 no-op fast path, null guard, scale<1
 * guard, and {@link ImageOps#resize(BufferedImage, int, int, int)} dimension / type
 * correctness.
 */
class ImageOpsTest {

    private static BufferedImage checkerboard(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = ((x + y) % 2 == 0) ? 0xFFFF0000 : 0xFF00FF00;
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    // --- upscaleNearestNeighbor: positive path ---

    @Test
    void upscaleNearestNeighbor_factor5Produces5xDimensions() {
        BufferedImage src = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

        BufferedImage out = ImageOps.upscaleNearestNeighbor(src, 5);

        assertThat(out.getWidth()).isEqualTo(160);
        assertThat(out.getHeight()).isEqualTo(160);
    }

    @Test
    void upscaleNearestNeighbor_preservesSourcePixelColorAtTopLeft() {
        // Fill source pixel (0,0) with a unique color; nearest-neighbor must reproduce it
        // exactly at dst(0..scale-1, 0..scale-1). No blending.
        BufferedImage src = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        int color = 0xFF1A2B3C;
        src.setRGB(0, 0, color);

        BufferedImage out = ImageOps.upscaleNearestNeighbor(src, 5);

        assertThat(out.getRGB(0, 0)).isEqualTo(color);
    }

    @Test
    void upscaleNearestNeighbor_isNearestNotInterpolated() {
        // Classic nearest-neighbor diagnostic: a 2x1 checkerboard scaled by 4 must still
        // contain only the two source colours - any interpolation mode would introduce
        // blended intermediates.
        BufferedImage src = checkerboard(2, 1); // pixel(0,0)=red, pixel(1,0)=green
        int red = src.getRGB(0, 0);
        int green = src.getRGB(1, 0);

        BufferedImage out = ImageOps.upscaleNearestNeighbor(src, 4);

        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int px = out.getRGB(x, y);
                assertThat(px)
                        .as("pixel (%d, %d) must be either source color", x, y)
                        .isIn(red, green);
            }
        }
    }

    // --- upscaleNearestNeighbor: guard paths ---

    @Test
    void upscaleNearestNeighbor_scaleOneReturnsSameInstance() {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        BufferedImage out = ImageOps.upscaleNearestNeighbor(src, 1);

        assertThat(out).isSameAs(src);
    }

    @Test
    void upscaleNearestNeighbor_nullSrcThrowsNpeWithMessage() {
        assertThatThrownBy(() -> ImageOps.upscaleNearestNeighbor(null, 2))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("src must not be null");
    }

    @Test
    void upscaleNearestNeighbor_scaleZeroThrowsIae() {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        assertThatThrownBy(() -> ImageOps.upscaleNearestNeighbor(src, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be >= 1");
    }

    @Test
    void upscaleNearestNeighbor_negativeScaleThrowsIae() {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        assertThatThrownBy(() -> ImageOps.upscaleNearestNeighbor(src, -3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be >= 1");
    }

    // --- resize: positive path ---

    @Test
    void resize_producesRequestedDimensionsAndType() {
        BufferedImage src = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage out = ImageOps.resize(src, 50, 25, BufferedImage.TYPE_INT_ARGB);

        assertThat(out.getWidth()).isEqualTo(50);
        assertThat(out.getHeight()).isEqualTo(25);
        assertThat(out.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void resize_targetTypeCanDifferFromSourceType() {
        BufferedImage src = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

        BufferedImage out = ImageOps.resize(src, 20, 20, BufferedImage.TYPE_INT_ARGB);

        assertThat(out.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
    }

    // --- resize: guard paths ---

    @Test
    void resize_nullSrcThrowsNpeWithMessage() {
        assertThatThrownBy(() -> ImageOps.resize(null, 10, 10, BufferedImage.TYPE_INT_ARGB))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("src must not be null");
    }

    @Test
    void resize_zeroWidthThrowsIae() {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        assertThatThrownBy(() -> ImageOps.resize(src, 0, 10, BufferedImage.TYPE_INT_ARGB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("w and h must be >= 1");
    }

    @Test
    void resize_zeroHeightThrowsIae() {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        assertThatThrownBy(() -> ImageOps.resize(src, 10, 0, BufferedImage.TYPE_INT_ARGB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("w and h must be >= 1");
    }
}
