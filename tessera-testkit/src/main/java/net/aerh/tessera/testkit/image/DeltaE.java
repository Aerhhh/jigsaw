package net.aerh.tessera.testkit.image;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * CIE76 colour-difference computation over sRGB inputs.
 *
 * <p>Public entry point: {@link #pixelsOverThreshold(BufferedImage, BufferedImage, double)}
 * walks two images pixel-by-pixel, computes {@link #deltaE76} on their CIE-L*a*b*
 * representations, and returns the fraction of pixels whose delta strictly exceeds the
 * threshold. Used by {@link GoldenImageAssertion} as the localised hotspot gate that
 * complements the global {@link Ssim} floor.
 *
 * <p>Colour pipeline: sRGB (0..255 per channel) is gamma-decoded to linear RGB, projected
 * into CIE XYZ under the D65 reference white, and finally mapped through the Lab
 * non-linearity. The pipeline matches the canonical
 * <a href="https://en.wikipedia.org/wiki/SRGB">sRGB</a> and
 * <a href="https://en.wikipedia.org/wiki/CIELAB_color_space">CIELAB</a> definitions.
 * deltaE76 is adequate for a threshold-5 hotspot gate; deltaE2000 would be perceptually
 * more accurate but roughly triples the code surface and is not needed for our use case.
 *
 * <p>Alpha is ignored - differences on fully transparent pixels are still counted. Golden
 * fixtures should render with a deterministic background colour to avoid alpha churn.
 */
public final class DeltaE {

    // D65 reference white, scaled so Yn = 1.0. Source: CIE 15:2004 Table 6.
    private static final double Xn = 0.95047;
    private static final double Yn = 1.0;
    private static final double Zn = 1.08883;

    // Lab non-linearity knee values per the canonical piecewise formula.
    private static final double DELTA = 6.0 / 29.0;
    private static final double DELTA_CUBED = DELTA * DELTA * DELTA;       // 216/24389
    private static final double INV_KAPPA_SLOPE = 1.0 / (3.0 * DELTA * DELTA); // 841/108
    private static final double DELTA_OFFSET = 4.0 / 29.0;

    private DeltaE() {
        /* utility class */
    }

    /**
     * Fraction of pixels whose CIE76 colour difference strictly exceeds {@code threshold}.
     *
     * <p>Equivalent-dimension images are required; the comparison is pixel-aligned. Uses the
     * batched {@link BufferedImage#getRGB(int, int, int, int, int[], int, int)} form to keep
     * the per-pixel constant factor low on larger inputs.
     *
     * @param expected the reference image; must not be {@code null}
     * @param actual the image under test; must not be {@code null}
     * @param threshold the strict deltaE76 cutoff; a pixel is counted when its delta is
     *                  <em>strictly greater than</em> this value
     * @return a fraction in {@code [0.0, 1.0]}: {@code 0.0} when no pixel exceeds the
     * threshold, {@code 1.0} when every pixel does
     * @throws NullPointerException if either image is {@code null}
     * @throws IllegalArgumentException if the dimensions do not match
     */
    public static double pixelsOverThreshold(BufferedImage expected, BufferedImage actual, double threshold) {
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
            return 0.0;
        }

        int[] exp = expected.getRGB(0, 0, w, h, null, 0, w);
        int[] act = actual.getRGB(0, 0, w, h, null, 0, w);

        long total = (long) w * (long) h;
        long over = 0L;
        for (int i = 0; i < exp.length; i++) {
            int a = exp[i];
            int b = act[i];
            if (a == b) {
                continue; // fast path: identical sRGB pixel -> delta 0
            }
            double[] labA = rgbToLab((a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF);
            double[] labB = rgbToLab((b >> 16) & 0xFF, (b >> 8) & 0xFF, b & 0xFF);
            if (deltaE76(labA, labB) > threshold) {
                over++;
            }
        }
        return (double) over / (double) total;
    }

    /**
     * Converts an sRGB triplet (channels in {@code 0..255}) to CIE-L*a*b* under D65.
     * Package-private for white-point sanity tests.
     *
     * @param r red channel in {@code 0..255}
     * @param g green channel in {@code 0..255}
     * @param b blue channel in {@code 0..255}
     * @return a 3-element array {@code [L, a, b]}. {@code L} lies in {@code [0, 100]};
     * {@code a} and {@code b} are unbounded in principle but stay within roughly
     * {@code [-128, 127]} for sRGB gamut inputs.
     */
    static double[] rgbToLab(int r, int g, int b) {
        double lr = gammaDecode(r / 255.0);
        double lg = gammaDecode(g / 255.0);
        double lb = gammaDecode(b / 255.0);

        // sRGB -> XYZ (D65) via the canonical matrix.
        double x = lr * 0.4124564 + lg * 0.3575761 + lb * 0.1804375;
        double y = lr * 0.2126729 + lg * 0.7151522 + lb * 0.0721750;
        double z = lr * 0.0193339 + lg * 0.1191920 + lb * 0.9503041;

        double fx = labF(x / Xn);
        double fy = labF(y / Yn);
        double fz = labF(z / Zn);

        double L = 116.0 * fy - 16.0;
        double a = 500.0 * (fx - fy);
        double bb = 200.0 * (fy - fz);
        return new double[]{L, a, bb};
    }

    /**
     * Euclidean distance between two Lab triplets (CIE76 formula).
     * Package-private to let tests exercise boundary cases directly.
     */
    static double deltaE76(double[] lab1, double[] lab2) {
        double dL = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];
        return Math.sqrt(dL * dL + da * da + db * db);
    }

    /** sRGB gamma decode: canonical piecewise form per IEC 61966-2-1. */
    private static double gammaDecode(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Lab f(t) non-linearity: cube-root above the knee, linear below. */
    private static double labF(double t) {
        return t > DELTA_CUBED ? Math.cbrt(t) : INV_KAPPA_SLOPE * t + DELTA_OFFSET;
    }
}
