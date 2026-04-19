package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.FromNbtBuilder;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.nbt.NbtFormat;
import net.aerh.tessera.api.nbt.ParsedItem;
import net.aerh.tessera.core.nbt.NbtDialectDetector;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Package-private fluent {@link FromNbtBuilder} implementation. Constructed via
 * {@link DefaultEngine#fromNbt(String)}.
 *
 * <p>Parsed NBT is currently routed through {@link ItemRequest} only; richer ParsedItem
 * dispatch (e.g. emitting an {@code InventoryRequest} for shulker-box NBT) is deferred to
 * the broader render-pipeline port.
 *
 * <p>Dialect routing is exposed via {@link #format(NbtFormat)}. Default behaviour
 * (no explicit format) invokes {@link NbtDialectDetector}; ambiguous input throws
 * {@code ParseException} wrapped as {@link RenderFailedException}. Explicit formats
 * (other than {@link NbtFormat#AUTO}) skip the detector entirely.
 */
public final class FromNbtBuilderImpl implements FromNbtBuilder {

    private final DefaultEngine engine;
    private final String nbt;
    private GenerationContext context = GenerationContext.defaults();
    /** Null when AUTO / unset; detector runs at render time. */
    private NbtFormat explicitFormat;

    public FromNbtBuilderImpl(DefaultEngine engine, String nbt) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.nbt = Objects.requireNonNull(nbt, "nbt must not be null");
    }

    @Override
    public FromNbtBuilder context(GenerationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        return this;
    }

    @Override
    public FromNbtBuilder format(NbtFormat format) {
        Objects.requireNonNull(format, "format must not be null");
        // AUTO collapses to null (same semantics as unset).
        this.explicitFormat = (format == NbtFormat.AUTO) ? null : format;
        return this;
    }

    @Override
    public GeneratorResult render() {
        try {
            ensureDetectable();
            ParsedItem parsed = engine.parseNbt(nbt);
            ItemRequest request = translate(parsed);
            return engine.renderInternal(request, context);
        } catch (RenderException | ParseException e) {
            throw new RenderFailedException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<GeneratorResult> renderAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureDetectable();
                ParsedItem parsed = engine.parseNbt(nbt);
                ItemRequest request = translate(parsed);
                return engine.renderInternal(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(new RenderFailedException(e.getMessage(), e));
            }
        }, engine.executor());
    }

    /**
     * When the builder is in AUTO mode, invokes {@link NbtDialectDetector} as an early
     * ambiguity gate. An empty result becomes a {@link ParseException} with the
     * {@code ".format(NbtFormat.X)"} remediation hint. When an explicit format is set,
     * this method is a no-op and the downstream parser chain handles routing.
     */
    private void ensureDetectable() throws ParseException {
        if (explicitFormat != null) {
            return;
        }
        Optional<NbtFormat> detected = NbtDialectDetector.detect(nbt);
        if (detected.isEmpty()) {
            throw new ParseException(
                    "ambiguous NBT input - call .format(NbtFormat.X) to disambiguate");
        }
    }

    /**
     * Translates a {@link ParsedItem} into the internal {@link ItemRequest} the engine renders.
     * Currently maps only itemId + enchanted + dyeColor; lore + display name (today routed
     * through the tooltip pipeline) and player-head texture (routed through the player-head
     * pipeline) will be wired in a later iteration.
     */
    private static ItemRequest translate(ParsedItem parsed) {
        return new ItemRequest(
                parsed.itemId(),
                parsed.enchanted(),
                false,
                1,
                Optional.empty(),
                parsed.dyeColor());
    }
}
