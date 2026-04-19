package net.aerh.tessera.testkit.image;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Pure-Java Structural Similarity Index (SSIM) computation.
 *
 * <p>Operates on pairs of {@link BufferedImage} inputs directly; no disk round-trips in the
 * hot path. Returns a scalar in {@code [0.0, 1.0]} where {@code 1.0} is identical and values
 * close to {@code 0.0} indicate structurally unrelated images.
 *
 * <p>Implementation: canonical SSIM formulation from Wang et al. 2004 (<em>Image Quality
 * Assessment: From Error Visibility to Structural Similarity</em>, IEEE Trans. Image
 * Processing). An 8x8 non-overlapping sliding window walks both images computing per-window
 * luminance, variance, and covariance; the per-window SSIM values are averaged to yield the
 * final index. Grayscale conversion uses the ITU-R BT.601 luma formula
 * ({@code Y = 0.299*R + 0.587*G + 0.114*B}). Stabilisation constants are
 * {@code C1 = (0.01 * 255)^2} and {@code C2 = (0.03 * 255)^2} per the canonical paper.
 *
 * <p>Intended as the first gate of {@link GoldenImageAssertion}; paired with
 * {@link DeltaE#pixelsOverThreshold} to enforce the two-gate fidelity check
 * (global SSIM floor + local deltaE hotspot coverage).
 *
 * <p>Practical input ceiling is around 1024x1024; the 8x8-window loop is {@code O(w*h)} but
 * the per-pixel constant factor trades accuracy for speed. Callers passing huge inputs
 * (e.g. 16k x 16k) may exhaust heap before completing - document scope is test-utility.
 *
 * @implNote Adapted from {@code rhys-e/structural-similarity} (MIT licensed); see
 * {@code LICENSE-thirdparty} in this module's resources for the full upstream notice.
 * Upstream read {@link java.io.File} inputs internally; this port takes {@link BufferedImage}
 * directly to eliminate disk round-trips in the golden-assertion hot path. The 8x8
 * luminance/variance/covariance math is unchanged.
 */
public final class Ssim {

    /** Stabilisation constant per the canonical SSIM paper; prevents division blow-up on flat luminance. */
    private static final double C1 = (0.01 * 255.0) * (0.01 * 255.0);
    /** Stabilisation constant per the canonical SSIM paper; prevents division blow-up on flat variance. */
    private static final double C2 = (0.03 * 255.0) * (0.03 * 255.0);

    /** Sliding-window edge length in pixels. 8x8 is the canonical default. */
    private static final int WINDOW = 8;

    private Ssim() {
        /* utility class */
    }

    /**
     * Computes the SSIM index between two images.
     *
     * <p>Both images must have matching width and height. Pixels are converted to grayscale
     * via BT.601 luma before the 8x8-window statistics are computed.
     *
     * @param expected the reference image; must not be {@code null}
     * @param actual the image under test; must not be {@code null}
     * @return a scalar in {@code [0.0, 1.0]}; {@code 1.0} indicates identical inputs
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the dimensions do not match
     */
    public static double compare(BufferedImage expected, BufferedImage actual) {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(actual, "actual must not be null");

        int w = expected.getWidth();
        int h = expected.getHeight();
        if (w != actual.getWidth() || h != actual.getHeight()) {
            throw new IllegalArgumentException(String.format(
                    "image dimensions must match (expected=%dx%d, actual=%dx%d)",
                    w, h, actual.getWidth(), actual.getHeight()));
        }
        if (w == 0 || h == 0) {
            // An empty image is trivially identical to itself.
            return 1.0;
        }

        double[] expLuma = toLuma(expected);
        double[] actLuma = toLuma(actual);

        double total = 0.0;
        int windows = 0;

        // Non-overlapping windows; the final partial row/column (w%8, h%8) is skipped,
        // matching the upstream reference and keeping per-window statistics well-defined.
        for (int y = 0; y + WINDOW <= h; y += WINDOW) {
            for (int x = 0; x + WINDOW <= w; x += WINDOW) {
                total += windowSsim(expLuma, actLuma, w, x, y);
                windows++;
            }
        }

        if (windows == 0) {
            // Images smaller than the 8x8 window: compare as a single smaller window using
            // whatever dimensions we have so callers don't get NaN. Equivalent to the
            // "average of zero windows" degenerate case.
            return singleRegionSsim(expLuma, actLuma, w, h);
        }

        return total / windows;
    }

    /**
     * Computes SSIM for a single 8x8 window anchored at {@code (x0, y0)}.
     */
    private static double windowSsim(double[] a, double[] b, int stride, int x0, int y0) {
        double sumA = 0.0;
        double sumB = 0.0;
        int count = WINDOW * WINDOW;
        for (int y = 0; y < WINDOW; y++) {
            int row = (y0 + y) * stride + x0;
            for (int x = 0; x < WINDOW; x++) {
                sumA += a[row + x];
                sumB += b[row + x];
            }
        }
        double meanA = sumA / count;
        double meanB = sumB / count;

        double varA = 0.0;
        double varB = 0.0;
        double cov = 0.0;
        for (int y = 0; y < WINDOW; y++) {
            int row = (y0 + y) * stride + x0;
            for (int x = 0; x < WINDOW; x++) {
                double da = a[row + x] - meanA;
                double db = b[row + x] - meanB;
                varA += da * da;
                varB += db * db;
                cov += da * db;
            }
        }
        varA /= count;
        varB /= count;
        cov /= count;

        double numerator = (2.0 * meanA * meanB + C1) * (2.0 * cov + C2);
        double denominator = (meanA * meanA + meanB * meanB + C1) * (varA + varB + C2);
        return numerator / denominator;
    }

    /**
     * Degenerate fallback for images smaller than the window - treats the whole image as one
     * region. Rare on real inputs; keeps small-input tests from returning NaN.
     */
    private static double singleRegionSsim(double[] a, double[] b, int w, int h) {
        int count = w * h;
        double sumA = 0.0;
        double sumB = 0.0;
        for (int i = 0; i < count; i++) {
            sumA += a[i];
            sumB += b[i];
        }
        double meanA = sumA / count;
        double meanB = sumB / count;

        double varA = 0.0;
        double varB = 0.0;
        double cov = 0.0;
        for (int i = 0; i < count; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            varA += da * da;
            varB += db * db;
            cov += da * db;
        }
        varA /= count;
        varB /= count;
        cov /= count;

        double numerator = (2.0 * meanA * meanB + C1) * (2.0 * cov + C2);
        double denominator = (meanA * meanA + meanB * meanB + C1) * (varA + varB + C2);
        return numerator / denominator;
    }

    /**
     * Decodes {@code img} into a flat luminance array using BT.601 luma. Batched via
     * {@link BufferedImage#getRGB(int, int, int, int, int[], int, int)} so the per-pixel
     * constant factor stays low on large inputs.
     */
    private static double[] toLuma(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] rgba = img.getRGB(0, 0, w, h, null, 0, w);
        double[] luma = new double[w * h];
        for (int i = 0; i < rgba.length; i++) {
            int pixel = rgba[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            luma[i] = 0.299 * r + 0.587 * g + 0.114 * b;
        }
        return luma;
    }
}
