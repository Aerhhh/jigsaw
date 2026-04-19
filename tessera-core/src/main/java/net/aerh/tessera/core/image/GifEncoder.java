package net.aerh.tessera.core.image;

import com.madgag.gif.fmsware.AnimatedGifEncoder;
import net.aerh.tessera.api.image.AnimatedEncoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Thin wrapper around {@code com.madgag:animated-gif-lib:1.4} (Kevin Weiner encoder).
 *
 * <p>Implements {@link AnimatedEncoder} per CONTEXT.md. The Kevin Weiner encoder is
 * the same engine Marmalade used internally, so switching this dependency direct (rather
 * than via Marmalade's JitPack SNAPSHOT) is an import swap, not a behaviour change. Parity
 * is confirmed via {@code MarmaladeParityTest.enchantedDiamondSword_gifFramesMatchByPsnr}.
 */
public final class GifEncoder implements AnimatedEncoder {

    @Override
    public String formatId() {
        return "gif";
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

        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!encoder.start(baos)) {
            throw new IOException("AnimatedGifEncoder.start() returned false");
        }
        encoder.setDelay(frameDelayMs);
        encoder.setRepeat(loop ? 0 : -1); // GIF89a: 0 = infinite, -1 = no loop
        for (int i = 0; i < frames.size(); i++) {
            if (!encoder.addFrame(frames.get(i))) {
                throw new IOException("AnimatedGifEncoder.addFrame(" + i + ") returned false");
            }
        }
        if (!encoder.finish()) {
            throw new IOException("AnimatedGifEncoder.finish() returned false");
        }
        return baos.toByteArray();
    }
}
