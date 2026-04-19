package net.aerh.tessera.core.image;

import dev.matrixlab.webp4j.WebPCodec;
import dev.matrixlab.webp4j.animation.FrameNormalizer;
import dev.matrixlab.webp4j.gif.GifToWebPConfig;
import net.aerh.tessera.api.image.AnimatedEncoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Thin wrapper around {@code dev.matrixlab.webp4j:webp4j-core:2.1.1} producing WebP byte arrays.
 *
 * <p>Replaces the Marmalade animated- and static-WebP encode paths. Implements
 * {@link AnimatedEncoder} so
 * {@link net.aerh.tessera.api.generator.GeneratorResult.AnimatedImage} can hold it behind the
 * interface and an alternative implementation can be swapped in via {@link java.util.ServiceLoader}.
 *
 * <p>Calls {@link FrameNormalizer#normalize(List)} on the input frames before encoding
 * (Decision 2A). libwebp's {@code createAnimatedWebP} requires all frames to share an
 * identical canvas size and pixel layout; {@code FrameNormalizer} enforces that without
 * silently distorting input. The PSNR >= 45 dB threshold in
 * {@code MarmaladeParityTest} acts as a safety net in case normalisation alters pixel
 * colours for mismatched inputs.
 */
public final class Webp4jEncoder implements AnimatedEncoder {

    @Override
    public String formatId() {
        return "webp";
    }

    @Override
    public byte[] encodeAnimated(List<BufferedImage> frames, int frameDelayMs, boolean loop)
            throws IOException {
        Objects.requireNonNull(frames, "frames must not be null");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        if (frameDelayMs < 0) {
            throw new IllegalArgumentException(
                    "frameDelayMs must be >= 0, got: " + frameDelayMs);
        }

        List<BufferedImage> normalized = FrameNormalizer.normalize(frames);
        int[] delays = new int[normalized.size()];
        Arrays.fill(delays, frameDelayMs);

        GifToWebPConfig config = GifToWebPConfig.createLosslessConfig();
        // libwebp treats loopCount=0 as infinite; any positive value is a finite loop count.
        // Use 0 for "loop forever" and 1 for "play once" to match Marmalade's semantics.
        config.setLoopCount(loop ? 0 : 1);

        return WebPCodec.createAnimatedWebP(normalized, delays, config);
    }

    /**
     * Static-image encoder convenience; not part of {@link AnimatedEncoder}.
     *
     * @param frame the source frame; must not be {@code null}
     * @param lossless whether to use lossless compression
     * @return the encoded WebP bytes
     * @throws IOException if encoding fails
     * @throws NullPointerException if {@code frame} is null
     */
    public byte[] encodeStatic(BufferedImage frame, boolean lossless) throws IOException {
        Objects.requireNonNull(frame, "frame must not be null");
        return lossless
                ? WebPCodec.encodeLosslessImage(frame)
                : WebPCodec.encodeImage(frame, 90.0f);
    }
}
