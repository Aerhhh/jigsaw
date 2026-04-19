package net.aerh.tessera.api.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Checked exception thrown when an HTTP download fails (non-200 status, {@link java.io.IOException},
 * or interruption). Mirrors the shape of {@link RenderException} - {@code extends Exception},
 * carries a diagnostic context map, exposes {@link #getContext()}.
 */
public class TesseraAssetDownloadException extends Exception {

    private final Map<String, Object> context;

    /**
     * Constructs a {@code TesseraAssetDownloadException} with the given message and no diagnostic
     * context.
     *
     * @param message a human-readable description of the download failure
     */
    public TesseraAssetDownloadException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    /**
     * Constructs a {@code TesseraAssetDownloadException} with the given message and diagnostic
     * context.
     *
     * @param message the human-readable description of the download failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public TesseraAssetDownloadException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Constructs a {@code TesseraAssetDownloadException} with the given message, diagnostic
     * context, and cause.
     *
     * @param message the human-readable description of the download failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause the underlying exception that triggered this failure
     */
    public TesseraAssetDownloadException(String message, Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Returns an unmodifiable map of diagnostic key-value pairs attached to this exception.
     */
    public Map<String, Object> getContext() {
        return context;
    }
}
