package net.aerh.tessera.api.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Centralised factory for {@link Graphics2D} instances with pixel-perfect Minecraft
 * rendering hints pre-applied. Every {@code *Generator} in the engine routes through
 * this factory.
 *
 * <p>Default hints (all locked by the factory):
 * <ul>
 *   <li>{@link RenderingHints#KEY_ANTIALIASING} = {@link RenderingHints#VALUE_ANTIALIAS_OFF}</li>
 *   <li>{@link RenderingHints#KEY_TEXT_ANTIALIASING} = {@link RenderingHints#VALUE_TEXT_ANTIALIAS_OFF}</li>
 *   <li>{@link RenderingHints#KEY_FRACTIONALMETRICS} = {@link RenderingHints#VALUE_FRACTIONALMETRICS_OFF}</li>
 *   <li>{@link RenderingHints#KEY_INTERPOLATION} = {@link RenderingHints#VALUE_INTERPOLATION_NEAREST_NEIGHBOR}</li>
 * </ul>
 * These are locked because any smoothing, antialiasing, or sub-pixel interpolation
 * destroys the pixel-perfect Minecraft fidelity that Tessera exists to produce.
 *
 * <p>Callers MUST call {@link Graphics2D#dispose()} when done - the factory does not
 * track handles. Use in a try-with-resources wrapper or a {@code try/finally}.
 */
public final class Graphics2DFactory {

    private Graphics2DFactory() {
        /* static-only */
    }

    /**
     * Creates a {@link Graphics2D} from the given {@link BufferedImage}, already configured
     * with Tessera's mandatory rendering hints.
     *
     * @param image the target image; must not be {@code null}
     * @return a configured {@code Graphics2D}; caller is responsible for {@code dispose()}
     * @throws NullPointerException if {@code image} is {@code null}
     */
    public static Graphics2D createGraphics(BufferedImage image) {
        Objects.requireNonNull(image, "image must not be null");
        Graphics2D g = image.createGraphics();
        applyDefaultHints(g);
        return g;
    }

    /**
     * Applies Tessera's mandatory rendering hints to an existing {@link Graphics2D}.
     * Available for callers that create their own {@link BufferedImage} (e.g. atlases)
     * or that need to reset hints mid-render.
     *
     * @param g the graphics context to configure; must not be {@code null}
     * @throws NullPointerException if {@code g} is {@code null}
     */
    public static void applyDefaultHints(Graphics2D g) {
        Objects.requireNonNull(g, "g must not be null");
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }
}
