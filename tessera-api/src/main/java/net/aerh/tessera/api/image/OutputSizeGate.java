package net.aerh.tessera.api.image;

import net.aerh.tessera.api.exception.OutputTooLargeException;

import java.util.Objects;

/**
 * Post-encode byte-size gate enforcing per-output-type caps. Called by encoder
 * boundaries ({@code GeneratorResult.StaticImage.toBytes()} /
 * {@code AnimatedImage.toWebpBytes()} / {@code toGifBytes()}) to fail fast when the
 * encoded output exceeds the admin-configured cap.
 *
 * <p>Lives in {@code tessera-api.image} (moved from {@code tessera-core.image} )
 * because {@code GeneratorResult} is the public-api return type that must invoke the
 * gate at the encoder boundary, and {@code tessera-api} cannot compile-depend on
 * {@code tessera-core} .
 *
 * <p>Defaults (rationale: Discord free-tier attachment ceiling):
 * <ul>
 *   <li>{@link #DEFAULT_STATIC_CAP}: 8 MB for static PNG / WebP output.</li>
 *   <li>{@link #DEFAULT_ANIMATED_CAP}: 24 MB for animated WebP / GIF output.</li>
 * </ul>
 *
 * <p>Cap resolution precedence (highest first):
 * <ol>
 *   <li>Builder override via {@code EngineBuilder#staticOutputCapBytes} /
 *       {@code animatedOutputCapBytes}.</li>
 *   <li>Environment variable {@code TESSERA_STATIC_OUTPUT_CAP_BYTES} /
 *       {@code TESSERA_ANIMATED_OUTPUT_CAP_BYTES}.</li>
 *   <li>The default values above.</li>
 * </ol>
 *
 * <p>Malformed env values (non-parseable longs) fall through to the default - never the
 * builder value, which is the whole point of the override precedence. A plain
 * {@link System#err} warning is emitted so the misconfiguration is visible; we do not
 * pull in an SLF4J dependency from {@code tessera-api} (which stays logger-independent).
 */
public final class OutputSizeGate {

    /** 8 MB static cap  (Discord free-tier attachment ceiling). */
    public static final long DEFAULT_STATIC_CAP = 8L * 1024 * 1024;

    /** 24 MB animated cap. */
    public static final long DEFAULT_ANIMATED_CAP = 24L * 1024 * 1024;

    static final String STATIC_ENV = "TESSERA_STATIC_OUTPUT_CAP_BYTES";
    static final String ANIMATED_ENV = "TESSERA_ANIMATED_OUTPUT_CAP_BYTES";

    private OutputSizeGate() {
    }

    /**
     * Returns {@code bytes} unchanged when its length is within {@code capBytes}, otherwise
     * throws {@link OutputTooLargeException}.
     *
     * <p>Threshold is strict: {@code bytes.length == capBytes} is accepted;
     * {@code bytes.length == capBytes + 1} is rejected.
     *
     * @param bytes the encoded output to check; must not be {@code null}
     * @param capBytes the maximum allowed byte length
     * @param renderType short descriptor of the render type (e.g. {@code "item"})
     * @return {@code bytes} unchanged when within the cap
     * @throws OutputTooLargeException if the encoded output exceeds {@code capBytes}
     * @throws NullPointerException if {@code bytes} or {@code renderType} is {@code null}
     */
    public static byte[] checkOrThrow(byte[] bytes, long capBytes, String renderType) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(renderType, "renderType must not be null");
        if (bytes.length > capBytes) {
            throw new OutputTooLargeException(bytes.length, capBytes, renderType);
        }
        return bytes;
    }

    /**
     * Resolves the effective static-output cap: {@code builderOverride} wins when non-null;
     * otherwise the {@value #STATIC_ENV} env var is parsed; otherwise
     * {@link #DEFAULT_STATIC_CAP}.
     */
    public static long resolveStaticCap(Long builderOverride) {
        if (builderOverride != null) {
            return builderOverride;
        }
        return parseEnvOrDefault(STATIC_ENV, DEFAULT_STATIC_CAP);
    }

    /**
     * Resolves the effective animated-output cap: {@code builderOverride} wins when non-null;
     * otherwise the {@value #ANIMATED_ENV} env var is parsed; otherwise
     * {@link #DEFAULT_ANIMATED_CAP}.
     */
    public static long resolveAnimatedCap(Long builderOverride) {
        if (builderOverride != null) {
            return builderOverride;
        }
        return parseEnvOrDefault(ANIMATED_ENV, DEFAULT_ANIMATED_CAP);
    }

    private static long parseEnvOrDefault(String envKey, long fallback) {
        String env = System.getenv(envKey);
        if (env == null) {
            return fallback;
        }
        try {
            return Long.parseLong(env.trim());
        } catch (NumberFormatException e) {
            // api stays logger-independent; emit a visible warning without pulling in slf4j.
            System.err.println("[tessera] Invalid " + envKey + " value '" + env
                    + "'; using default " + fallback);
            return fallback;
        }
    }
}
