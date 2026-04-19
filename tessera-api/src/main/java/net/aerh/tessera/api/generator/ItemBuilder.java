package net.aerh.tessera.api.generator;

import java.util.concurrent.CompletableFuture;

/**
 * Terminal-fluent builder for item renders. Obtain via {@link net.aerh.tessera.api.Engine#item()}.
 *
 * <p>Implements {@link RenderSpec} so it composes with
 * {@link net.aerh.tessera.api.Engine#composite()}'s fan-out entry point:
 *
 * <pre>{@code
 * GeneratorResult result = engine.item()
 *         .itemId("diamond_sword")
 *         .enchanted(true)
 *         .durabilityPercent(0.45)
 *         .render();
 * }</pre>
 *
 * <p>Per post-plan-check amendment, {@link #render()} and {@link #renderAsync()}
 * declare no checked throws; failures surface as the unchecked
 * {@link net.aerh.tessera.api.exception.RenderFailedException}.
 *
 * @see net.aerh.tessera.api.Engine#item()
 */
public interface ItemBuilder extends RenderSpec {

    ItemBuilder itemId(String itemId);

    ItemBuilder enchanted(boolean enchanted);

    ItemBuilder hovered(boolean hovered);

    ItemBuilder scale(int scale);

    ItemBuilder durabilityPercent(double percent);

    ItemBuilder dyeColor(int color);

    ItemBuilder color(String nameOrHex);

    ItemBuilder context(GenerationContext context);

    GeneratorResult render();

    CompletableFuture<GeneratorResult> renderAsync();
}
