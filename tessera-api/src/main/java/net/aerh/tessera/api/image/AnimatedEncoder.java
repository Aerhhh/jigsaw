package net.aerh.tessera.api.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Public interface for animated-image encoders. Serves as an escape hatch: if the
 * golden-image harness exposes quantiser drift in {@code com.madgag:animated-gif-lib:1.4}
 * (last released 2017), an alternative GIF encoder can be shipped and registered via
 * {@link java.util.ServiceLoader} without changing any call site.
 *
 * <p>Implementations are discovered by {@link net.aerh.tessera.api.generator.GeneratorResult}
 * using {@link java.util.ServiceLoader} keyed by {@link #formatId()} ({@code "gif"} or
 * {@code "webp"}). Concrete implementations live in {@code tessera-core.image}; consumers do
 * not construct encoders directly.
 *
 * <p>Impl-site restriction is enforced by the ArchUnit rule in {@code tessera-core}
 * ({@code CoreBoundaryTest.only_core_image_package_may_implement_animated_encoder}).
 *
 * <p>Implementations are expected to be stateless and cheap to instantiate;
 * {@link net.aerh.tessera.api.generator.GeneratorResult.AnimatedImage} holds one instance per
 * format as a {@code private static final} field.
 */
public interface AnimatedEncoder {

    /**
     * Returns the canonical format id for this encoder, used by ServiceLoader lookup in
     * {@link net.aerh.tessera.api.generator.GeneratorResult.AnimatedImage}.
     *
     * @return the format id (e.g. {@code "gif"} or {@code "webp"}); never {@code null}
     */
    String formatId();

    /**
     * Encodes the supplied frames as a single animated-image byte stream.
     *
     * @param frames the ordered animation frames; must not be {@code null} or empty
     * @param frameDelayMs the per-frame delay in milliseconds; must be {@code >= 0}
     * @param loop whether the output should loop indefinitely
     *                     ({@code true} = infinite, {@code false} = play once)
     * @return the encoded image bytes
     * @throws IOException if the underlying encoder reports a failure
     * @throws NullPointerException if {@code frames} is null
     * @throws IllegalArgumentException if {@code frames} is empty or {@code frameDelayMs} is
     *                                  negative
     */
    byte[] encodeAnimated(List<BufferedImage> frames, int frameDelayMs, boolean loop)
            throws IOException;
}
