package net.aerh.tessera.api.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Checked exception thrown when downloaded asset bytes fail SHA1 verification or when a manifest
 * entry's path escapes the cache directory.
 *
 * <p>Mirrors the shape of {@link RenderException} - {@code extends Exception}, carries a
 * diagnostic context map, exposes {@link #getContext()}.
 */
public class TesseraAssetIntegrityException extends Exception {

    private final Map<String, Object> context;

    /**
     * Constructs a {@code TesseraAssetIntegrityException} with the given message and no diagnostic
     * context.
     *
     * @param message a human-readable description of the integrity failure
     */
    public TesseraAssetIntegrityException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    /**
     * Constructs a {@code TesseraAssetIntegrityException} with the given message and diagnostic
     * context.
     *
     * @param message the human-readable description of the integrity failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public TesseraAssetIntegrityException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Constructs a {@code TesseraAssetIntegrityException} with the given message, diagnostic
     * context, and cause.
     *
     * @param message the human-readable description of the integrity failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause the underlying exception that triggered this failure
     */
    public TesseraAssetIntegrityException(String message, Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Convenience constructor for the common SHA1-mismatch case.
     *
     * @param path the manifest-relative path of the mismatched file
     * @param expectedSha1 the SHA1 declared in the manifest
     * @param actualSha1 the SHA1 of the downloaded bytes
     */
    public TesseraAssetIntegrityException(String path, String expectedSha1, String actualSha1) {
        this(
                "SHA1 mismatch for " + path + " (expected " + expectedSha1 + ", got " + actualSha1 + ")",
                Map.of("path", path, "expectedSha1", expectedSha1, "actualSha1", actualSha1)
        );
    }

    /**
     * Returns an unmodifiable map of diagnostic key-value pairs attached to this exception.
     */
    public Map<String, Object> getContext() {
        return context;
    }
}
