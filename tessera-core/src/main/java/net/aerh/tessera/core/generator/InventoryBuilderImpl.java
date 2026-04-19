package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.InventoryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Package-private fluent {@link InventoryBuilder} implementation. Constructed via
 * {@link DefaultEngine#inventory()}.
 */
public final class InventoryBuilderImpl implements InventoryBuilder, InternalRequestSource {

    private final DefaultEngine engine;
    private int rows = 6;
    private int slotsPerRow = 9;
    private String title = "";
    private boolean drawTitle;
    private boolean drawBorder = true;
    private boolean drawBackground = true;
    private final List<InventoryItem> items = new ArrayList<>();
    private int scale = 1;
    private GenerationContext context = GenerationContext.defaults();

    public InventoryBuilderImpl(DefaultEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    @Override
    public InventoryBuilder rows(int rows) {
        this.rows = rows;
        return this;
    }

    @Override
    public InventoryBuilder slotsPerRow(int slotsPerRow) {
        this.slotsPerRow = slotsPerRow;
        return this;
    }

    @Override
    public InventoryBuilder title(String title) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.drawTitle = !title.isBlank();
        return this;
    }

    @Override
    public InventoryBuilder drawTitle(boolean drawTitle) {
        this.drawTitle = drawTitle;
        return this;
    }

    @Override
    public InventoryBuilder drawBorder(boolean drawBorder) {
        this.drawBorder = drawBorder;
        return this;
    }

    @Override
    public InventoryBuilder drawBackground(boolean drawBackground) {
        this.drawBackground = drawBackground;
        return this;
    }

    @Override
    public InventoryBuilder withInventoryString(String inventoryString) {
        Objects.requireNonNull(inventoryString, "inventoryString must not be null");
        if (inventoryString.isBlank()) {
            return this;
        }
        // Delegate to InventoryRequest.Builder so the package-private InventoryStringParser
        // stays sealed inside the.generator package; we lift the resulting items list out.
        InventoryRequest parsed = InventoryRequest.builder()
                .rows(rows)
                .slotsPerRow(slotsPerRow)
                .withInventoryString(inventoryString)
                .build();
        this.items.addAll(parsed.items());
        return this;
    }

    @Override
    public InventoryBuilder scale(int scale) {
        this.scale = Math.max(1, Math.min(64, scale));
        return this;
    }

    @Override
    public InventoryBuilder context(GenerationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        return this;
    }

    @Override
    public GeneratorResult render() {
        InventoryRequest request = buildRequest();
        try {
            return engine.renderInternal(request, context);
        } catch (RenderException | ParseException e) {
            throw new RenderFailedException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<GeneratorResult> renderAsync() {
        InventoryRequest request = buildRequest();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return engine.renderInternal(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(new RenderFailedException(e.getMessage(), e));
            }
        }, engine.executor());
    }

    private InventoryRequest buildRequest() {
        return new InventoryRequest(rows, slotsPerRow, title, drawTitle, drawBorder, drawBackground,
                items, scale);
    }

    @Override
    public InventoryRequest toInternalRequest() {
        return buildRequest();
    }
}
