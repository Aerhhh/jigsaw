package net.aerh.tessera.api.exception;

import java.util.Map;

/**
 * Unchecked wrapper thrown by the fluent {@code *Builder.render()} terminals (in
 * {@code tessera-core}) when the underlying {@code DefaultEngine#renderInternal} call
 * produces a {@link RenderException} or {@link ParseException}. Consumers of the public
 * fluent api never see a checked exception — all render failures surface as this
 * unchecked type with {@link #getCause()} preserving the original.
 *
 * <p>The 7 api-level builder interfaces ({@code ItemBuilder}, {@code TooltipBuilder}...)
 * declare {@code GeneratorResult render()} with NO {@code throws} clause; the concrete
 * {@code *BuilderImpl} classes in tessera-core catch {@code RenderException} /
 * {@code ParseException} and rethrow as {@code RenderFailedException}. {@code renderAsync()}
 * wraps the same unchecked exception inside {@link java.util.concurrent.CompletionException}
 * via the standard CompletableFuture convention.
 */
public final class RenderFailedException extends TesseraException {

    public RenderFailedException(String message, Throwable cause) {
        super(message,
                cause instanceof TesseraException tesEx ? tesEx.getContext() : Map.of(),
                cause);
    }
}
