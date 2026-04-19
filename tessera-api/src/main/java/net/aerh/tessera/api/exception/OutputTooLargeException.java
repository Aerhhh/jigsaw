package net.aerh.tessera.api.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Unchecked exception raised when a rendered output's encoded byte length exceeds the
 * configured size cap.
 *
 * <p>This unchecked exception lives in {@code tessera-api.exception}; the
 * enforcement is done by {@code net.aerh.tessera.core.image.OutputSizeGate} at the encoder
 * boundary.
 *
 * <p>The exception carries a structured diagnostic context (actualBytes, capBytes,
 * renderType) so downstream consumers (Discord bots, HTTP layer) can log the failure
 * without having to parse the message string. Deliberately does NOT carry the encoded
 * bytes themselves; leaking an oversized payload through the cause chain would defeat
 * the point of the cap.
 */
public final class OutputTooLargeException extends TesseraException {

    private final long actualBytes;
    private final long capBytes;
    private final String renderType;

    /**
     * Constructs an {@code OutputTooLargeException}.
     *
     * @param actualBytes the actual byte length of the encoded output
     * @param capBytes the configured cap which was exceeded
     * @param renderType a short descriptor of the render type (e.g. {@code "item"},
     *                    {@code "animated-webp"}); must not be {@code null}
     * @throws NullPointerException if {@code renderType} is {@code null}
     */
    public OutputTooLargeException(long actualBytes, long capBytes, String renderType) {
        super(buildMessage(actualBytes, capBytes,
                        Objects.requireNonNull(renderType, "renderType must not be null")),
                Map.of(
                        "actualBytes", actualBytes,
                        "capBytes", capBytes,
                        "renderType", renderType));
        this.actualBytes = actualBytes;
        this.capBytes = capBytes;
        this.renderType = renderType;
    }

    private static String buildMessage(long actualBytes, long capBytes, String renderType) {
        return String.format(
                "Render output (%d bytes, %s) exceeded cap of %d bytes. "
                        + "Lower scale or split the composite.",
                actualBytes, renderType, capBytes);
    }

    /**
     * Returns the actual byte length of the encoded output that exceeded the cap.
     */
    public long actualBytes() {
        return actualBytes;
    }

    /**
     * Returns the configured cap in bytes which was exceeded.
     */
    public long capBytes() {
        return capBytes;
    }

    /**
     * Returns a short descriptor of the render type that produced the oversized output.
     */
    public String renderType() {
        return renderType;
    }
}
