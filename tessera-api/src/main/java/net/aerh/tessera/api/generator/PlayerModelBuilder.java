package net.aerh.tessera.api.generator;

import java.util.concurrent.CompletableFuture;

/**
 * Terminal-fluent builder for full-body isometric player-model renders. Obtain via
 * {@link net.aerh.tessera.api.Engine#playerModel()}. Setter names lift from the
 * existing {@code PlayerModelRequest.Builder}.
 *
 * <p>Armor is represented opaquely via {@link Object} on the api-level interface;
 * concrete armor types (ArmorSet, ArmorPiece) live in {@code tessera-core} per the
 * module-split rules and are supplied to {@link #armor(Object)} at the call site.
 *
 * <p>Per post-plan-check amendment, {@link #render()} and {@link #renderAsync()}
 * declare no checked throws; failures surface as the unchecked
 * {@link net.aerh.tessera.api.exception.RenderFailedException}.
 *
 * @see net.aerh.tessera.api.Engine#playerModel()
 */
public interface PlayerModelBuilder extends RenderSpec {

    PlayerModelBuilder username(String username);

    PlayerModelBuilder base64Texture(String base64);

    PlayerModelBuilder textureUrl(String url);

    PlayerModelBuilder playerName(String playerName);

    /**
     * Sets the armor set to render on the model. Accepts the tessera-core
     * {@code ArmorSet} type opaquely to avoid api→core dependency.
     *
     * @param armorSet a tessera-core ArmorSet instance; must not be {@code null}
     * @return this builder
     */
    PlayerModelBuilder armor(Object armorSet);

    PlayerModelBuilder slim(boolean slim);

    PlayerModelBuilder scale(int scale);

    PlayerModelBuilder context(GenerationContext context);

    GeneratorResult render();

    CompletableFuture<GeneratorResult> renderAsync();
}
