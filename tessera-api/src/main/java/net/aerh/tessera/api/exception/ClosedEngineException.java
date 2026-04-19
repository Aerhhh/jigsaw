package net.aerh.tessera.api.exception;

/**
 * Thrown by {@link net.aerh.tessera.api.Engine}'s fluent terminal-chain terminals
 * (or its {@code renderAsync} equivalents) when the engine has already been closed via
 * {@link net.aerh.tessera.api.Engine#close()}.
 *
 * <p>Unchecked, extends {@link IllegalStateException}; closed engines are a
 * programming error (use-after-close), not an expected recoverable condition.
 *
 * <p>A second {@code close()} call is a no-op; {@code ClosedEngineException} is only
 * thrown on render attempts after the first close completes.
 */
public final class ClosedEngineException extends IllegalStateException {

    public ClosedEngineException() {
        super("Engine is closed; subsequent render() / renderAsync() calls are rejected");
    }

    public ClosedEngineException(String message) {
        super(message);
    }
}
