package net.aerh.tessera.core.image;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Pure-JDK pixel-perfect image scaling operations.
 *
 * <p>Replaces the Marmalade helpers {@code ImageUtil.upscaleImage} and
 * {@code ImageUtil.resizeImage} with {@link AffineTransformOp} +
 * {@link AffineTransformOp#TYPE_NEAREST_NEIGHBOR}. This is the exact transformation
 * Marmalade itself wraps, exposed as JDK code so we can drop the JitPack dependency.
 *
 * @see AffineTransformOp
 */
public final class ImageOps {

    private ImageOps() {
        // static-only
    }

    /**
     * Returns {@code src} upscaled by integer {@code scale} using nearest-neighbor interpolation.
     *
     * @param src the source image; must not be {@code null}
     * @param scale the integer scale factor; must be &gt;= 1
     * @return a new {@link BufferedImage#TYPE_INT_ARGB} image of size
     *         {@code src.width*scale} x {@code src.height*scale}, or {@code src} itself
     *         when {@code scale == 1}
     * @throws NullPointerException if {@code src} is null
     * @throws IllegalArgumentException if {@code scale &lt; 1}
     */
    public static BufferedImage upscaleNearestNeighbor(BufferedImage src, int scale) {
        Objects.requireNonNull(src, "src must not be null");
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be >= 1, got: " + scale);
        }
        if (scale == 1) {
            return src;
        }

        int w = src.getWidth() * scale;
        int h = src.getHeight() * scale;
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        AffineTransformOp op = new AffineTransformOp(
                AffineTransform.getScaleInstance(scale, scale),
                AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        op.filter(src, dst);
        return dst;
    }

    /**
     * Returns {@code src} resized to {@code w}x{@code h} using nearest-neighbor interpolation.
     *
     * @param src the source image; must not be {@code null}
     * @param w target width in pixels; must be &gt;= 1
     * @param h target height in pixels; must be &gt;= 1
     * @param imgType the result's {@link BufferedImage} image-type constant
     *                (e.g. {@link BufferedImage#TYPE_INT_ARGB})
     * @return a new {@code BufferedImage} of the requested dimensions and type
     * @throws NullPointerException if {@code src} is null
     * @throws IllegalArgumentException if {@code w} or {@code h} is less than 1
     */
    public static BufferedImage resize(BufferedImage src, int w, int h, int imgType) {
        Objects.requireNonNull(src, "src must not be null");
        if (w < 1 || h < 1) {
            throw new IllegalArgumentException("w and h must be >= 1, got: " + w + "x" + h);
        }
        BufferedImage dst = new BufferedImage(w, h, imgType);
        double sx = (double) w / src.getWidth();
        double sy = (double) h / src.getHeight();
        AffineTransformOp op = new AffineTransformOp(
                AffineTransform.getScaleInstance(sx, sy),
                AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        op.filter(src, dst);
        return dst;
    }
}
