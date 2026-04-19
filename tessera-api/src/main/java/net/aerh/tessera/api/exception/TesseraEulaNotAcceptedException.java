package net.aerh.tessera.api.exception;

import java.util.Map;

/**
 * Thrown by {@link net.aerh.tessera.api.assets.TesseraAssets#fetch(String)} when the Mojang EULA
 * has not been accepted via any of the three supported paths:
 * <ul>
 *   <li>{@link net.aerh.tessera.api.EngineBuilder#acceptMojangEula(boolean)} (programmatic)</li>
 *   <li>environment variable {@code TESSERA_ACCEPT_MOJANG_EULA=true}</li>
 *   <li>system property {@code -Dtessera.accept.mojang.eula=true}</li>
 * </ul>
 *
 * <p>Unchecked because the fix is a one-line configuration change at the call site, not a runtime
 * recovery path - direct analog of {@link ValidationException}.
 */
public class TesseraEulaNotAcceptedException extends TesseraException {

    /**
     * Constructs a {@code TesseraEulaNotAcceptedException} with the given message.
     *
     * @param message a human-readable description of the missing EULA acceptance
     */
    public TesseraEulaNotAcceptedException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code TesseraEulaNotAcceptedException} with the given message and diagnostic
     * context.
     *
     * @param message the human-readable description of the missing EULA acceptance
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public TesseraEulaNotAcceptedException(String message, Map<String, Object> context) {
        super(message, context);
    }

    /**
     * Constructs a {@code TesseraEulaNotAcceptedException} with the given message, diagnostic
     * context, and cause.
     *
     * @param message the human-readable description of the missing EULA acceptance
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause the underlying exception that triggered this failure
     */
    public TesseraEulaNotAcceptedException(String message, Map<String, Object> context, Throwable cause) {
        super(message, context, cause);
    }
}
