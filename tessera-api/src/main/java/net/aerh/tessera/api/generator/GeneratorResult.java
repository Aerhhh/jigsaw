package net.aerh.tessera.api.generator;

import net.aerh.tessera.api.image.AnimatedEncoder;
import net.aerh.tessera.api.image.OutputSizeGate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The output of a rendering operation, which is either a single static image or an animated
 * sequence of frames.
 *
 * <p>This is a sealed interface with two permitted implementations: {@link StaticImage} and
 * {@link AnimatedImage}. Use Java pattern matching to handle each case:
 *
 * <pre>{@code
 * GeneratorResult result = engine.item().itemId("diamond_sword").render();
 *
 * switch (result) {
 *     case GeneratorResult.StaticImage s  -> saveAsPng(s.image());
 *     case GeneratorResult.AnimatedImage a -> saveAsGif(a.frames(), a.frameDelayMs());
 * }
 * }</pre>
 *
 * <p>If you only need the first frame regardless of animation state, call {@link #firstFrame()}.
 *
 * <p>Both {@link StaticImage#toBytes()} and {@link AnimatedImage#toGifBytes()} /
 * {@link AnimatedImage#toWebpBytes()} invoke {@link OutputSizeGate#checkOrThrow} at the
 * encoder boundary. When the result is constructed with an explicit cap
 * (e.g. {@link StaticImage#StaticImage(BufferedImage, long)}) that cap is used; when
 * constructed with the default 1-arg constructor the cap falls through to
 * {@link OutputSizeGate#resolveStaticCap(Long)} / {@link OutputSizeGate#resolveAnimatedCap(Long)}
 * (env-or-default).
 *
 * @see StaticImage
 * @see AnimatedImage
 */
public sealed interface GeneratorResult {

    /**
     * Returns the first (or only) frame of this result.
     * For a {@link StaticImage} this is the sole image; for an {@link AnimatedImage} this is
     * the first frame in the sequence.
     *
     * @return the first frame; never {@code null}
     */
    BufferedImage firstFrame();

    /**
     * Returns {@code true} if this result contains multiple animation frames.
     *
     * @return {@code true} for {@link AnimatedImage}, {@code false} for {@link StaticImage}
     */
    boolean isAnimated();

    /**
     * A single-frame (non-animated) rendering result.
     *
     * <p>{@code staticCapBytes} carries the engine-configured cap for post-encode size
     * gating. A value of {@code 0} (produced by the default 1-arg constructor)
     * means "resolve via {@link OutputSizeGate#resolveStaticCap(Long)} at encode time"
     * - which honours the {@code TESSERA_STATIC_OUTPUT_CAP_BYTES} env and the
     * {@link OutputSizeGate#DEFAULT_STATIC_CAP} default. When {@code DefaultEngine}
     * constructs the result it stamps the builder-resolved cap directly so the engine
     * override wins over env / default.
     *
     * @param image the rendered image; must not be {@code null}
     * @param staticCapBytes the engine-resolved static cap, or {@code 0} for env/default resolution
     */
    record StaticImage(BufferedImage image, long staticCapBytes) implements GeneratorResult {

        /**
         * Convenience constructor that stamps {@code staticCapBytes = 0}, signalling that
         * {@link #toBytes()} should resolve the cap via env / default at encode time.
         *
         * <p>Generator impls that do not have an engine reference construct via this
         * overload; {@code DefaultEngine} replaces the result with the 2-arg form when
         * it needs to stamp an explicit builder-resolved cap.
         *
         * @param image the rendered image; must not be {@code null}
         */
        public StaticImage(BufferedImage image) {
            this(image, 0L);
        }

        @Override
        public BufferedImage firstFrame() {
            return image;
        }

        @Override
        public boolean isAnimated() {
            return false;
        }

        /**
         * Encodes this static image as PNG bytes.
         *
         * <p>The encoded output is passed through {@link OutputSizeGate#checkOrThrow}
         * against the effective cap (explicit {@link #staticCapBytes} when non-zero,
         * otherwise {@link OutputSizeGate#resolveStaticCap(Long)} which honours
         * {@code TESSERA_STATIC_OUTPUT_CAP_BYTES} env + default).
         *
         * @return the PNG-encoded byte array; never {@code null} or empty
         * @throws UncheckedIOException if PNG encoding fails
         * @throws net.aerh.tessera.api.exception.OutputTooLargeException if the encoded
         *     output exceeds the effective cap (post-encode fail-fast)
         */
        public byte[] toBytes() {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                byte[] encoded = baos.toByteArray();
                long effectiveCap = staticCapBytes > 0
                        ? staticCapBytes
                        : OutputSizeGate.resolveStaticCap(null);
                return OutputSizeGate.checkOrThrow(encoded, effectiveCap, "static-png");
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to encode PNG", e);
            }
        }
    }

    /**
     * A multi-frame animated rendering result.
     *
     * <p>The compact constructor validates that the frame list is non-empty and makes a defensive
     * copy of the supplied list.
     *
     * <p>{@code animatedCapBytes} carries the engine-configured cap for post-encode size
     * gating. A value of {@code 0} (produced by the default 2-arg constructor)
     * means "resolve via {@link OutputSizeGate#resolveAnimatedCap(Long)} at encode time"
     * - which honours the {@code TESSERA_ANIMATED_OUTPUT_CAP_BYTES} env and the
     * {@link OutputSizeGate#DEFAULT_ANIMATED_CAP} default. When {@code DefaultEngine}
     * constructs the result it stamps the builder-resolved cap directly so the engine
     * override wins over env / default.
     *
     * @param frames the ordered list of animation frames; must not be empty
     * @param frameDelayMs the delay between frames in milliseconds
     * @param animatedCapBytes the engine-resolved animated cap, or {@code 0} for env/default resolution
     * @throws IllegalArgumentException if {@code frames} is empty
     */
    record AnimatedImage(List<BufferedImage> frames, int frameDelayMs, long animatedCapBytes) implements GeneratorResult {

        // AnimatedEncoder impls must declare formatId() and ship
        // META-INF/services/net.aerh.tessera.api.image.AnimatedEncoder descriptor before
        // toGifBytes()/toWebpBytes() can be invoked. Resolution is lazy (inside the
        // toGifBytes / toWebpBytes methods) so that classloading AnimatedImage does not
        // require a wired ServiceLoader descriptor - tests that only construct the record
        // (e.g. shape / frame-list validation) stay green even before the descriptor is
        // wired. Encoders are stateless; safe to share JVM-wide once resolved.
        private static final class EncoderHolder {
            private static final AnimatedEncoder WEBP_ENCODER = loadEncoderByName("webp");
            private static final AnimatedEncoder GIF_ENCODER = loadEncoderByName("gif");
        }

        /**
         * Compact constructor that validates the frame list is non-empty and copies it defensively.
         *
         * @throws IllegalArgumentException if {@code frames} is empty
         */
        public AnimatedImage {
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("Animated image must have at least one frame");
            }
            frames = List.copyOf(frames);
        }

        /**
         * Convenience constructor that stamps {@code animatedCapBytes = 0}, signalling that
         * {@link #toGifBytes()} / {@link #toWebpBytes()} should resolve the cap via env /
         * default at encode time.
         *
         * <p>Generator impls that do not have an engine reference construct via this
         * overload; {@code DefaultEngine} replaces the result with the 3-arg form when
         * it needs to stamp an explicit builder-resolved cap.
         *
         * @param frames the ordered list of animation frames; must not be empty
         * @param frameDelayMs the delay between frames in milliseconds
         */
        public AnimatedImage(List<BufferedImage> frames, int frameDelayMs) {
            this(frames, frameDelayMs, 0L);
        }

        @Override
        public BufferedImage firstFrame() {
            return frames.getFirst();
        }

        @Override
        public boolean isAnimated() {
            return true;
        }

        /**
         * Encodes all frames into an animated GIF and returns the raw bytes.
         *
         * <p>The GIF is encoded with the frame delay stored in this result and is set to loop
         * indefinitely. The encoded output is passed through
         * {@link OutputSizeGate#checkOrThrow} against the effective cap (explicit
         * {@link #animatedCapBytes} when non-zero, otherwise
         * {@link OutputSizeGate#resolveAnimatedCap(Long)}).
         *
         * @return the GIF-encoded byte array; never {@code null} or empty
         * @throws UncheckedIOException if GIF encoding fails
         * @throws net.aerh.tessera.api.exception.OutputTooLargeException if the encoded
         *     output exceeds the effective cap (post-encode fail-fast)
         */
        public byte[] toGifBytes() {
            try {
                byte[] encoded = EncoderHolder.GIF_ENCODER.encodeAnimated(frames, frameDelayMs, true);
                return OutputSizeGate.checkOrThrow(encoded, effectiveAnimatedCap(), "animated-gif");
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to encode animated GIF", e);
            }
        }

        /**
         * Encodes all frames into an animated WebP and returns the raw bytes.
         *
         * <p>The WebP is encoded with the frame delay stored in this result and is set to loop
         * indefinitely. Uses lossless compression to preserve pixel-art fidelity. The encoded
         * output is passed through {@link OutputSizeGate#checkOrThrow} against the effective
         * cap (explicit {@link #animatedCapBytes} when non-zero, otherwise
         * {@link OutputSizeGate#resolveAnimatedCap(Long)}).
         *
         * @return the WebP-encoded byte array; never {@code null} or empty
         *
         * @throws UncheckedIOException if WebP encoding fails
         * @throws net.aerh.tessera.api.exception.OutputTooLargeException if the encoded
         *     output exceeds the effective cap (post-encode fail-fast)
         */
        public byte[] toWebpBytes() {
            try {
                byte[] encoded = EncoderHolder.WEBP_ENCODER.encodeAnimated(frames, frameDelayMs, true);
                return OutputSizeGate.checkOrThrow(encoded, effectiveAnimatedCap(), "animated-webp");
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to encode animated WebP", e);
            }
        }

        private long effectiveAnimatedCap() {
            return animatedCapBytes > 0
                    ? animatedCapBytes
                    : OutputSizeGate.resolveAnimatedCap(null);
        }

        /**
         * Looks up an {@link AnimatedEncoder} by {@link AnimatedEncoder#formatId()} via
         * {@link ServiceLoader}. Impls live in {@code tessera-core.image} and register via
         * a {@code META-INF/services/net.aerh.tessera.api.image.AnimatedEncoder} descriptor.
         */
        private static AnimatedEncoder loadEncoderByName(String id) {
            return ServiceLoader.load(AnimatedEncoder.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(e -> id.equals(e.formatId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No AnimatedEncoder registered with formatId=" + id
                                    + ". Add tessera-core as a runtime dependency."));
        }
    }
}
