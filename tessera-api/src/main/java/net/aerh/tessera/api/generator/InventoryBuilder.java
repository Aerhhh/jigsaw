package net.aerh.tessera.api.generator;

import java.util.concurrent.CompletableFuture;

/**
 * Terminal-fluent builder for inventory renders. Obtain via
 * {@link net.aerh.tessera.api.Engine#inventory()}.
 *
 * <p>Consumers populate slots via {@link #withInventoryString(String)} (the existing
 * {@code %%}-delimited inventory string format) or via individual slot helpers the
 * {@code tessera-core} impl exposes. Setter names lift from the existing
 * {@code InventoryRequest.Builder}.
 *
 * <p>Per post-plan-check amendment, {@link #render()} and {@link #renderAsync()}
 * declare no checked throws; failures surface as the unchecked
 * {@link net.aerh.tessera.api.exception.RenderFailedException}.
 *
 * @see net.aerh.tessera.api.Engine#inventory()
 */
public interface InventoryBuilder extends RenderSpec {

    InventoryBuilder rows(int rows);

    InventoryBuilder slotsPerRow(int slotsPerRow);

    InventoryBuilder title(String title);

    InventoryBuilder drawTitle(boolean drawTitle);

    InventoryBuilder drawBorder(boolean drawBorder);

    InventoryBuilder drawBackground(boolean drawBackground);

    InventoryBuilder withInventoryString(String inventoryString);

    InventoryBuilder scale(int scale);

    InventoryBuilder context(GenerationContext context);

    GeneratorResult render();

    CompletableFuture<GeneratorResult> renderAsync();
}
