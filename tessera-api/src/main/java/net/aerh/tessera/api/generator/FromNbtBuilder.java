package net.aerh.tessera.api.generator;

import net.aerh.tessera.api.nbt.NbtFormat;

import java.util.concurrent.CompletableFuture;

/**
 * Terminal-fluent builder for NBT-sourced item renders. Obtain via
 * {@link net.aerh.tessera.api.Engine#fromNbt(String)}.
 *
 * <p>The NBT payload is supplied at construction via {@code engine.fromNbt(nbt)}; this
 * builder only exposes the shared render terminals and optional context setter.
 *
 * <p>Per post-plan-check amendment, {@link #render()} and {@link #renderAsync()}
 * declare no checked throws; failures surface as the unchecked
 * {@link net.aerh.tessera.api.exception.RenderFailedException}.
 *
 * @see net.aerh.tessera.api.Engine#fromNbt(String)
 */
public interface FromNbtBuilder extends RenderSpec {

    FromNbtBuilder context(GenerationContext context);

    /**
     * Overrides the NBT dialect auto-detection. When left unset (or
     * explicitly passed {@link NbtFormat#AUTO}), the impl invokes
     * {@code NbtDialectDetector.detect(nbt)} and fails with a
     * {@link net.aerh.tessera.api.exception.ParseException} (wrapped as
     * {@link net.aerh.tessera.api.exception.RenderFailedException}) when the payload
     * signature is ambiguous.
     *
     * <p>Any non-AUTO value skips the detector and threads the input directly into the
     * parser chain bound to that dialect.
     *
     * @param format the target NBT dialect; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code format} is {@code null}
     */
    FromNbtBuilder format(NbtFormat format);

    GeneratorResult render();

    CompletableFuture<GeneratorResult> renderAsync();
}
