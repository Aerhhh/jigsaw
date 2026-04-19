package net.aerh.tessera.api.exception;

import java.util.Map;

/**
 * Unchecked exception raised by {@link net.aerh.tessera.api.resource.ResourcePack} impls
 * when pack loading or asset lookup is refused for safety reasons (zip-slip, path-traversal,
 * malformed zip archives, missing required entries).
 *
 * <p>all new unchecked exceptions live in {@code tessera-api.exception};
 *   this is the umbrella type for both the {@code ZipResourcePack} and
 * {@code FolderResourcePack} impls shipped.
 *
 * <p>Callers should not attempt to recover from this exception - refused pack inputs are a
 * configuration or input-integrity bug. Log the message and fail the build-Engine call
 * path.
 */
public final class ResourcePackException extends TesseraException {

    /**
     * Constructs a {@code ResourcePackException} with the given message and no diagnostic
     * context.
     *
     * @param message a human-readable description of the refusal; must not be {@code null}
     */
    public ResourcePackException(String message) {
        super(message, Map.of());
    }

    /**
     * Constructs a {@code ResourcePackException} with the given message, an underlying
     * cause, and no diagnostic context.
     *
     * @param message a human-readable description of the refusal; must not be {@code null}
     * @param cause the underlying exception, typically an {@link java.io.IOException}
     */
    public ResourcePackException(String message, Throwable cause) {
        super(message, Map.of(), cause);
    }
}
