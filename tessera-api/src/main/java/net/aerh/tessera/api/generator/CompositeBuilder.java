package net.aerh.tessera.api.generator;

import java.util.concurrent.CompletableFuture;

/**
 * Terminal-fluent builder for composite renders — layouts of multiple child
 * {@link RenderSpec}s. Obtain via {@link net.aerh.tessera.api.Engine#composite()}.
 *
 * <pre>{@code
 * GeneratorResult composite = engine.composite()
 *         .horizontal()
 *         .add(engine.item().itemId("diamond_sword"))
 *         .add(engine.tooltip().line("Legendary"))
 *         .render();
 * }</pre>
 *
 * <p>Per post-plan-check amendment, {@link #render()} and {@link #renderAsync()}
 * declare no checked throws; failures surface as the unchecked
 * {@link net.aerh.tessera.api.exception.RenderFailedException}.
 *
 * @see net.aerh.tessera.api.Engine#composite()
 */
public interface CompositeBuilder extends RenderSpec {

    CompositeBuilder horizontal();

    CompositeBuilder vertical();

    CompositeBuilder grid(int cols, int rows);

    CompositeBuilder padding(int pixels);

    CompositeBuilder add(RenderSpec child);

    CompositeBuilder context(GenerationContext context);

    GeneratorResult render();

    CompletableFuture<GeneratorResult> renderAsync();
}
