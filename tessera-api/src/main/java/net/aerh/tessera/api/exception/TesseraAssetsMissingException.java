package net.aerh.tessera.api.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Checked exception thrown when {@link net.aerh.tessera.api.Engine#builder()} is {@code.build()}-
 * called but the assets named in the pinned manifest for the requested Minecraft version are not
 * present in the resolved cache directory. Remediation: call
 * {@link net.aerh.tessera.api.assets.TesseraAssets#fetch(String)} before {@code.build()}.
 *
 * <p>Mirrors the shape of {@link RenderException} - {@code extends Exception}, carries a
 * diagnostic context map, exposes {@link #getContext()}.
 *
 * @see net.aerh.tessera.api.assets.TesseraAssets
 */
public class TesseraAssetsMissingException extends Exception {

    private final Map<String, Object> context;

    /**
     * Constructs a {@code TesseraAssetsMissingException} with the given message and no diagnostic
     * context.
     *
     * @param message a human-readable description of the cache-state failure
     */
    public TesseraAssetsMissingException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    /**
     * Constructs a {@code TesseraAssetsMissingException} with the given message and diagnostic
     * context entries.
     *
     * @param message the human-readable description of the cache-state failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public TesseraAssetsMissingException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Constructs a {@code TesseraAssetsMissingException} with the given message, diagnostic
     * context, and cause.
     *
     * @param message the human-readable description of the cache-state failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause the underlying exception that triggered this failure
     */
    public TesseraAssetsMissingException(String message, Map<String, Object> context, Throwable cause) {
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
