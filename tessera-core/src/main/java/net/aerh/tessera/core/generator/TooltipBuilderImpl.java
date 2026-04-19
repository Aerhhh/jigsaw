package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.TooltipBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Package-private fluent {@link TooltipBuilder} implementation. Constructed via
 * {@link DefaultEngine#tooltip()}.
 */
public final class TooltipBuilderImpl implements TooltipBuilder, InternalRequestSource {

    private final DefaultEngine engine;
    private final List<String> lines = new ArrayList<>();
    private int alpha = TooltipRequest.DEFAULT_ALPHA;
    private int padding = TooltipRequest.DEFAULT_PADDING;
    private boolean firstLinePadding = true;
    private int maxLineLength = TooltipRequest.DEFAULT_MAX_LINE_LENGTH;
    private boolean centeredText;
    private boolean renderBorder = true;
    private int scaleFactor = 1;
    private GenerationContext context = GenerationContext.defaults();

    public TooltipBuilderImpl(DefaultEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    @Override
    public TooltipBuilder line(String line) {
        this.lines.add(Objects.requireNonNull(line, "line must not be null"));
        return this;
    }

    @Override
    public TooltipBuilder lines(List<String> lines) {
        Objects.requireNonNull(lines, "lines must not be null");
        this.lines.addAll(lines);
        return this;
    }

    @Override
    public TooltipBuilder alpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        return this;
    }

    @Override
    public TooltipBuilder padding(int padding) {
        this.padding = Math.max(0, padding);
        return this;
    }

    @Override
    public TooltipBuilder firstLinePadding(boolean firstLinePadding) {
        this.firstLinePadding = firstLinePadding;
        return this;
    }

    @Override
    public TooltipBuilder maxLineLength(int maxLineLength) {
        this.maxLineLength = Math.max(TooltipRequest.MIN_LINE_LENGTH, maxLineLength);
        return this;
    }

    @Override
    public TooltipBuilder centeredText(boolean centeredText) {
        this.centeredText = centeredText;
        return this;
    }

    @Override
    public TooltipBuilder renderBorder(boolean renderBorder) {
        this.renderBorder = renderBorder;
        return this;
    }

    @Override
    public TooltipBuilder scaleFactor(int scaleFactor) {
        this.scaleFactor = Math.max(1, scaleFactor);
        return this;
    }

    @Override
    public TooltipBuilder context(GenerationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        return this;
    }

    @Override
    public GeneratorResult render() {
        TooltipRequest request = buildRequest();
        try {
            return engine.renderInternal(request, context);
        } catch (RenderException | ParseException e) {
            throw new RenderFailedException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<GeneratorResult> renderAsync() {
        TooltipRequest request = buildRequest();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return engine.renderInternal(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(new RenderFailedException(e.getMessage(), e));
            }
        }, engine.executor());
    }

    private TooltipRequest buildRequest() {
        return new TooltipRequest(lines, alpha, padding, firstLinePadding, maxLineLength,
                centeredText, renderBorder, scaleFactor);
    }

    @Override
    public TooltipRequest toInternalRequest() {
        return buildRequest();
    }
}
