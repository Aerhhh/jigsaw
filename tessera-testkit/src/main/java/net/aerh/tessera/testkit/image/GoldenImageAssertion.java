package net.aerh.tessera.testkit.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Two-gate golden-image assertion: SSIM floor first (global), deltaE pixel-coverage second
 * (local hotspot). Both gates together close the gap that either alone would miss.
 *
 * <p>On failure, writes {@code actual.png}, {@code expected.png}, and a {@code diff.png}
 * heatmap to the directory named by the {@code tessera.golden.diffDir} system property, or
 * to {@code java.io.tmpdir} when that property is unset. The diff heatmap uses a linear
 * deltaE ramp from black (identical pixel) through mid-grey (deltaE = 10) to white
 * (deltaE >= 20), so anything over the {@code deltaE = 5} threshold is clearly visible.
 *
 * <p>The {@code -Dtessera.golden.update=true} flag toggles local baseline regeneration via
 * {@link #updateIfRequested}. CI (env {@code CI=true}) refuses regeneration with an
 * {@link IllegalStateException} so a PR cannot accidentally rewrite committed baselines.
 *
 * <p>Uses the {@link GoldenImageConfig#GLOBAL_SSIM_FLOOR} / {@link GoldenImageConfig#MAX_HOTSPOT_PCT}
 * defaults on the 2-arg overload; callers needing per-render-type thresholds pass them
 * explicitly to the 4-arg form.
 */
public final class GoldenImageAssertion {

    /**
     * SLF4J logger emitting the per-assertion {@code SSIM for <id>: <value>} one-liner
     * consumed by the calibration pipeline. The log line format is locked: the
     * calibration grep regex is {@code SSIM for [^:]+: [0-9.]+}. Do NOT change the
     * pattern without coordinating with the calibration script.
     */
    private static final Logger log = LoggerFactory.getLogger(GoldenImageAssertion.class);

    /** System property key for the baseline-regeneration flag. */
    private static final String UPDATE_PROP = "tessera.golden.update";

    /** System property key for the failure-artifact output directory (used in CI upload paths). */
    private static final String DIFF_DIR_PROP = "tessera.golden.diffDir";

    /** DeltaE threshold for the hotspot count. */
    private static final double HOTSPOT_DELTA_E = 5.0;

    /** DeltaE value at which the diff heatmap saturates to white. */
    private static final double DIFF_HEATMAP_MAX = 20.0;

    private GoldenImageAssertion() {
        /* utility class */
    }

    /**
     * Convenience two-argument assertion using the {@link GoldenImageConfig} global floor
     * and hotspot ceiling.
     *
     * @param actual the render under test; must not be {@code null}
     * @param expected the baseline render; must not be {@code null}
     * @throws NullPointerException if either input is {@code null}
     * @throws AssertionError if either gate fails; diff artifacts are written before the throw
     */
    public static void assertGoldenEquals(BufferedImage actual, BufferedImage expected) {
        assertGoldenEquals(actual, expected,
                GoldenImageConfig.GLOBAL_SSIM_FLOOR,
                GoldenImageConfig.MAX_HOTSPOT_PCT);
    }

    /**
     * Back-compat four-argument form. Delegates to
     * {@link #assertGoldenEquals(BufferedImage, BufferedImage, double, double, String)}
     * with a synthetic baseline id derived from image dimensions
     * ({@code "<width>x<height>-unnamed"}).
     *
     * <p>Prefer the five-argument form when the caller has a stable fixture id; the
     * synthetic id is only greppable by shape, not by semantic fixture name, so it does
     * not help calibration correlate SSIM scores to render-type buckets.
     *
     * @param actual the render under test; must not be {@code null}
     * @param expected the baseline render; must not be {@code null}
     * @param ssimFloor minimum acceptable SSIM index
     * @param maxHotspotPct maximum acceptable fraction of pixels exceeding deltaE = 5
     * @throws NullPointerException if either image input is {@code null}
     * @throws AssertionError on gate failure; diff artifacts written before the throw
     */
    public static void assertGoldenEquals(BufferedImage actual, BufferedImage expected,
                                          double ssimFloor, double maxHotspotPct) {
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
        String syntheticId = actual.getWidth() + "x" + actual.getHeight() + "-unnamed";
        assertGoldenEquals(actual, expected, ssimFloor, maxHotspotPct, syntheticId);
    }

    /**
     * Canonical five-argument form: explicit per-render-type thresholds plus a caller-
     * supplied {@code baselineId} for per-fixture SSIM logging.
     *
     * <p>Gate 1 (global): {@link Ssim#compare(BufferedImage, BufferedImage)} must be
     * greater than or equal to {@code ssimFloor}. Gate 2 (local): fewer than
     * {@code maxHotspotPct} of pixels may exceed deltaE = 5 via
     * {@link DeltaE#pixelsOverThreshold(BufferedImage, BufferedImage, double)}.
     *
     * <p>Before either gate fires, emits exactly one SLF4J {@code INFO} line in the
     * format {@code SSIM for <baselineId>: <ssim>}. The log call lives BEFORE the floor
     * check so calibration can read the actual (possibly sub-floor) score that tripped
     * the assertion; moving the log below the throw would hide failing-score data that
     * calibration needs to tune thresholds.
     *
     * <p><strong>Format lock:</strong> the log line format is consumed by
     * {@code grep -oE "SSIM for [^:]+: [0-9.]+"} in the calibration pipeline. Any change
     * to the prefix, separator, or number formatting breaks that pipeline. See
     * {@code CONTRIBUTING-BASELINES.md} for the contributor-facing procedure.
     *
     * @param actual the render under test; must not be {@code null}
     * @param expected the baseline render; must not be {@code null}
     * @param ssimFloor minimum acceptable SSIM index
     * @param maxHotspotPct maximum acceptable fraction of pixels exceeding deltaE = 5
     * @param baselineId the stable fixture id (e.g. {@code "item/diamond_sword.png"});
     *                       must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws AssertionError on gate failure; diff artifacts written before the throw
     */
    public static void assertGoldenEquals(BufferedImage actual, BufferedImage expected,
                                          double ssimFloor, double maxHotspotPct,
                                          String baselineId) {
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(baselineId, "baselineId must not be null");

        double ssim = Ssim.compare(expected, actual);

        // Format-locked log line consumed by the calibration pipeline. MUST emit before
        // the floor check so the failure path still exposes the score. Do NOT reformat.
        log.info("SSIM for {}: {}", baselineId, ssim);

        if (ssim < ssimFloor) {
            writeDiffArtifacts(expected, actual);
            throw new AssertionError(String.format(
                    "SSIM %.4f below floor %.4f", ssim, ssimFloor));
        }
        double hotspotPct = DeltaE.pixelsOverThreshold(expected, actual, HOTSPOT_DELTA_E);
        if (hotspotPct > maxHotspotPct) {
            writeDiffArtifacts(expected, actual);
            throw new AssertionError(String.format(
                    "%.3f%% of pixels exceed deltaE=%.1f (limit %.2f%%)",
                    hotspotPct * 100.0, HOTSPOT_DELTA_E, maxHotspotPct * 100.0));
        }
    }

    /**
     * Writes {@code actual} to {@code goldenPath} as PNG, creating parent directories as
     * needed. Refuses to run when {@code CI=true} is present in the environment so PRs
     * cannot silently rewrite committed baselines.
     *
     * @param actual the render to persist as the new baseline; must not be {@code null}
     * @param goldenPath the destination file; parents created if missing
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalStateException if running under {@code CI=true}
     * @throws UncheckedIOException on I/O failure
     */
    public static void regenerateBaseline(BufferedImage actual, Path goldenPath) {
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(goldenPath, "goldenPath must not be null");

        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            throw new IllegalStateException(
                    "Cannot regenerate golden baselines inside CI (env CI=true). Run locally on JDK 21.");
        }
        try {
            Path parent = goldenPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ImageIO.write(actual, "png", goldenPath.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write baseline " + goldenPath, e);
        }
    }

    /**
     * When {@code -Dtessera.golden.update=true} is set, delegates to
     * {@link #regenerateBaseline} and returns {@code true}. Otherwise a no-op that
     * returns {@code false}.
     *
     * @param actual the candidate baseline image; must not be {@code null}
     * @param goldenPath the destination file; must not be {@code null}
     * @return {@code true} if the baseline was regenerated, {@code false} if the flag was absent
     * @throws IllegalStateException if the flag is set but {@code CI=true}
     */
    public static boolean updateIfRequested(BufferedImage actual, Path goldenPath) {
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(goldenPath, "goldenPath must not be null");

        if (!"true".equalsIgnoreCase(System.getProperty(UPDATE_PROP))) {
            return false;
        }
        regenerateBaseline(actual, goldenPath);
        return true;
    }

    /**
     * Writes {@code actual.png}, {@code expected.png}, and {@code diff.png} to the directory
     * named by {@code tessera.golden.diffDir}, or {@code java.io.tmpdir} if the property is
     * unset. Swallows I/O errors (the assertion is about to throw anyway - we don't want to
     * swap a meaningful gate failure for an I/O stack trace).
     */
    private static void writeDiffArtifacts(BufferedImage expected, BufferedImage actual) {
        Path dir = resolveDiffDir();
        try {
            Files.createDirectories(dir);
            ImageIO.write(actual, "png", dir.resolve("actual.png").toFile());
            ImageIO.write(expected, "png", dir.resolve("expected.png").toFile());
            ImageIO.write(renderDiffHeatmap(expected, actual), "png",
                    dir.resolve("diff.png").toFile());
        } catch (IOException | RuntimeException ignored) {
            // Best-effort: the assertion failure is what matters. CI upload step tolerates
            // partial artifact sets.
        }
    }

    private static Path resolveDiffDir() {
        String override = System.getProperty(DIFF_DIR_PROP);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "tessera-golden-diffs");
    }

    /**
     * Produces a per-pixel deltaE heatmap. Identical pixels are black; deltaE >= 20 saturates
     * to white. Linear ramp in between so a pixel at the 5.0 threshold renders as mid-grey.
     */
    private static BufferedImage renderDiffHeatmap(BufferedImage expected, BufferedImage actual) {
        int w = Math.min(expected.getWidth(), actual.getWidth());
        int h = Math.min(expected.getHeight(), actual.getHeight());
        BufferedImage heat = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int[] exp = expected.getRGB(0, 0, w, h, null, 0, w);
        int[] act = actual.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[w * h];

        for (int i = 0; i < exp.length; i++) {
            int a = exp[i];
            int b = act[i];
            double delta;
            if (a == b) {
                delta = 0.0;
            } else {
                double[] labA = DeltaE.rgbToLab((a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF);
                double[] labB = DeltaE.rgbToLab((b >> 16) & 0xFF, (b >> 8) & 0xFF, b & 0xFF);
                delta = DeltaE.deltaE76(labA, labB);
            }
            int intensity = (int) Math.round(Math.min(1.0, delta / DIFF_HEATMAP_MAX) * 255.0);
            out[i] = (0xFF << 24) | (intensity << 16) | (intensity << 8) | intensity;
        }
        heat.setRGB(0, 0, w, h, out, 0, w);
        return heat;
    }
}
