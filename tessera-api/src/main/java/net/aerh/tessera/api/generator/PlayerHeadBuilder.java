package net.aerh.tessera.api.generator;

import java.util.concurrent.CompletableFuture;

/**
 * Terminal-fluent builder for player-head renders. Obtain via
 * {@link net.aerh.tessera.api.Engine#playerHead()}. Exactly one of
 * {@link #base64Texture(String)} / {@link #textureUrl(String)} / {@link #username(String)}
 * must be supplied; validation is internal to the impl per the existing
 * {@code PlayerHeadRequest.Builder} contract.
 *
 * <p>Per post-plan-check amendment, {@link #render()} and {@link #renderAsync()}
 * declare no checked throws; failures surface as the unchecked
 * {@link net.aerh.tessera.api.exception.RenderFailedException}.
 *
 * @see net.aerh.tessera.api.Engine#playerHead()
 */
public interface PlayerHeadBuilder extends RenderSpec {

    PlayerHeadBuilder username(String username);

    PlayerHeadBuilder base64Texture(String base64);

    PlayerHeadBuilder textureUrl(String url);

    PlayerHeadBuilder playerName(String playerName);

    PlayerHeadBuilder scale(int scale);

    PlayerHeadBuilder context(GenerationContext context);

    GeneratorResult render();

    CompletableFuture<GeneratorResult> renderAsync();
}
