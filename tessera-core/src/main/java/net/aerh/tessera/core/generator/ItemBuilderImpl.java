package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.ItemBuilder;
import net.aerh.tessera.api.text.ChatColor;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Package-private fluent {@link ItemBuilder} implementation. Constructed via
 * {@link DefaultEngine#item()} ; consumers never see this class directly.
 *
 * <p>{@link #render()} and {@link #renderAsync()} declare no checked throws;
 * {@link RenderException} / {@link ParseException} from the internal call site are wrapped
 * into the unchecked {@link RenderFailedException} so consumer code never has to catch a
 * checked exception on the fluent surface.
 */
public final class ItemBuilderImpl implements ItemBuilder, InternalRequestSource {

    private final DefaultEngine engine;
    private String itemId;
    private boolean enchanted;
    private boolean hovered;
    private int scale = 1;
    private Optional<Double> durabilityPercent = Optional.empty();
    private Optional<Integer> dyeColor = Optional.empty();
    private GenerationContext context = GenerationContext.defaults();

    public ItemBuilderImpl(DefaultEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    @Override
    public ItemBuilder itemId(String itemId) {
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        return this;
    }

    @Override
    public ItemBuilder enchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    @Override
    public ItemBuilder hovered(boolean hovered) {
        this.hovered = hovered;
        return this;
    }

    @Override
    public ItemBuilder scale(int scale) {
        this.scale = scale;
        return this;
    }

    @Override
    public ItemBuilder durabilityPercent(double percent) {
        this.durabilityPercent = Optional.of(percent);
        return this;
    }

    @Override
    public ItemBuilder dyeColor(int color) {
        this.dyeColor = Optional.of(color);
        return this;
    }

    @Override
    public ItemBuilder color(String nameOrHex) {
        Objects.requireNonNull(nameOrHex, "nameOrHex must not be null");
        this.dyeColor = Optional.of(resolveColor(nameOrHex));
        return this;
    }

    @Override
    public ItemBuilder context(GenerationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        return this;
    }

    @Override
    public GeneratorResult render() {
        ItemRequest request = buildRequest();
        try {
            return engine.renderInternal(request, context);
        } catch (RenderException | ParseException e) {
            throw new RenderFailedException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<GeneratorResult> renderAsync() {
        ItemRequest request = buildRequest();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return engine.renderInternal(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(new RenderFailedException(e.getMessage(), e));
            }
        }, engine.executor());
    }

    private ItemRequest buildRequest() {
        Objects.requireNonNull(itemId, "itemId must be set before render()");
        return new ItemRequest(itemId, enchanted, hovered, scale, durabilityPercent, dyeColor);
    }

    @Override
    public ItemRequest toInternalRequest() {
        return buildRequest();
    }

    /**
     * Resolves a Minecraft color name (e.g. {@code "red"}) or hex string ({@code "#FF0000"})
     * to a packed RGB integer. Mirrors the existing {@code ItemRequest.Builder.color(String)}
     * resolver so the fluent path produces identical results.
     */
    private static int resolveColor(String nameOrHex) {
        if (nameOrHex.startsWith("#")) {
            try {
                return Integer.parseInt(nameOrHex.substring(1), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid hex color: " + nameOrHex, e);
            }
        }
        ChatColor color = ChatColor.byName(nameOrHex);
        if (color != null) {
            return color.color().getRGB() & 0xFFFFFF;
        }
        throw new IllegalArgumentException("Unknown color: " + nameOrHex);
    }
}
