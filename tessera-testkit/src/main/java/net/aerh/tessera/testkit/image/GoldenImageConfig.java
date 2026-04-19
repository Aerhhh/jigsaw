package net.aerh.tessera.testkit.image;

/**
 * Central thresholds consumed by {@link GoldenImageAssertion}.
 *
 * <p>The two-gate model:
 * <ol>
 *   <li><strong>Global SSIM floor:</strong> every render must meet at least
 *   {@link #GLOBAL_SSIM_FLOOR} structural similarity against its baseline.</li>
 *   <li><strong>Pixel-coverage hotspot:</strong> fewer than {@link #MAX_HOTSPOT_PCT} of
 *   pixels may exceed deltaE = 5 against the baseline. Catches local regressions that a
 *   global SSIM average would hide.</li>
 * </ol>
 *
 * <p>Per-render-type overrides ship as additional constants; callers pass them explicitly
 * to the 4-arg {@link GoldenImageAssertion#assertGoldenEquals(java.awt.image.BufferedImage,
 * java.awt.image.BufferedImage, double, double)} form. Composite assertions use the
 * weakest-member-of-children rule (smaller SSIM floor, larger hotspot allowance) computed
 * at the call site.
 *
 * <p>All thresholds are provisional; a dedicated calibration pass will settle final values
 * against real fixtures across the JDK 21 + latest JDK matrix. Do not hand-tune here.
 */
public final class GoldenImageConfig {

    /**
     * Global SSIM floor. Every render must meet at least this value. 0.995 is the
     * canonical "visually identical" ceiling per the Wang et al. paper; anything below
     * and a human observer will notice differences side-by-side.
     */
    public static final double GLOBAL_SSIM_FLOOR = 0.995;

    /**
     * Pixel-coverage hotspot ceiling. Fraction of pixels allowed to exceed deltaE = 5;
     * 0.005 = 0.5%.
     */
    public static final double MAX_HOTSPOT_PCT = 0.005;

    // Per-render-type SSIM thresholds. These values are provisional starting points.
    // Calibration against a hydrated asset cache on a JDK 21 LTS + latest JDK matrix is
    // pending, because (1) no asset cache exists locally (~/.tessera/assets/26.1.2/ is
    // absent), (2) golden baseline PNG directories under
    // tessera-testkit/src/test/resources/golden/26.1.2/ are empty so baseline capture has
    // not yet happened, and (3) only JDK 21 is available on the execution host so a
    // cross-matrix comparison cannot be produced. A dedicated calibration pass will own
    // baseline capture + (if needed) per-fixture threshold overrides. Each constant below
    // carries an inline provisional marker so CI drift analysis can retag them to
    // `// calibrated <date> ...` atomically when the calibration run lands.

    public static final double ITEM_ICON_SSIM = 0.999;    // provisional - awaits calibration on CI matrix
    public static final double SKULL_ICON_SSIM = 0.999;   // provisional - awaits calibration on CI matrix
    public static final double TOOLTIP_SSIM = 0.98;       // provisional - awaits calibration on CI matrix
    public static final double INVENTORY_SSIM = 0.985;    // provisional - awaits calibration on CI matrix
    public static final double PLAYER_HEAD_SSIM = 0.999;  // provisional - awaits calibration on CI matrix
    public static final double PLAYER_MODEL_SSIM = 0.99;  // provisional - awaits calibration on CI matrix
    // composite = weakest-member-of-children (computed at assert site)

    private GoldenImageConfig() {
        /* utility class */
    }
}
