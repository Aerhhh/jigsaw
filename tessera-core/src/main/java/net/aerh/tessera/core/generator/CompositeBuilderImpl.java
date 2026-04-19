package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.CompositeBuilder;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.RenderRequest;
import net.aerh.tessera.api.generator.RenderSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Package-private fluent {@link CompositeBuilder} implementation. Constructed via
 * {@link DefaultEngine#composite()}.
 *
 * <p>Children supplied to {@link #add(RenderSpec)} must be Tessera-built BuilderImpls (which
 * also implement {@link InternalRequestSource}); external {@link RenderSpec} implementations
 * are rejected with an {@link IllegalArgumentException}  (only the engine's own
 * fluent builders are valid composite children, since they are the only ones that can name
 * the package-private internal record types).
 *
 * <p>{@link #grid(int, int)} is not supported in 1.0.0; the underlying {@link CompositeRequest}
 * carries only HORIZONTAL / VERTICAL layouts. Calling {@code grid(...)} throws
 * {@link UnsupportedOperationException}; the api signature is preserved so consumers can
 * compile against future versions that ship a grid layout.
 */
public final class CompositeBuilderImpl implements CompositeBuilder, InternalRequestSource {

    private final DefaultEngine engine;
    private final List<RenderRequest> children = new ArrayList<>();
    private CompositeRequest.Layout layout = CompositeRequest.Layout.HORIZONTAL;
    private int padding = 4;
    private int scaleFactor = 1;
    private int gridRows = 0;
    private int gridCols = 0;
    private GenerationContext context = GenerationContext.defaults();

    public CompositeBuilderImpl(DefaultEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    @Override
    public CompositeBuilder horizontal() {
        this.layout = CompositeRequest.Layout.HORIZONTAL;
        return this;
    }

    @Override
    public CompositeBuilder vertical() {
        this.layout = CompositeRequest.Layout.VERTICAL;
        return this;
    }

    /**
     *  (grid layout): accepts grid dims and selects {@link CompositeRequest.Layout#GRID}.
     * Note the public {@link net.aerh.tessera.api.generator.CompositeBuilder} signature is
     * {@code grid(int cols, int rows)} for historical reasons; we preserve that public contract
     * while storing rows and cols consistently on the resulting request.
     */
    @Override
    public CompositeBuilder grid(int cols, int rows) {
        if (rows < 1) {
            throw new IllegalArgumentException("grid rows must be >= 1, got: " + rows);
        }
        if (cols < 1) {
            throw new IllegalArgumentException("grid cols must be >= 1, got: " + cols);
        }
        this.layout = CompositeRequest.Layout.GRID;
        this.gridRows = rows;
        this.gridCols = cols;
        return this;
    }

    @Override
    public CompositeBuilder padding(int pixels) {
        if (pixels < 0) {
            throw new IllegalArgumentException("padding must be >= 0, got: " + pixels);
        }
        this.padding = pixels;
        return this;
    }

    @Override
    public CompositeBuilder add(RenderSpec child) {
        Objects.requireNonNull(child, "child must not be null");
        if (!(child instanceof InternalRequestSource source)) {
            throw new IllegalArgumentException(
                    "Only Tessera built-in fluent builders can be composite children; got: "
                            + child.getClass().getName());
        }
        this.children.add(source.toInternalRequest());
        return this;
    }

    @Override
    public CompositeBuilder context(GenerationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        return this;
    }

    @Override
    public GeneratorResult render() {
        CompositeRequest request = buildRequest();
        try {
            return engine.renderInternal(request, context);
        } catch (RenderException | ParseException e) {
            throw new RenderFailedException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<GeneratorResult> renderAsync() {
        CompositeRequest request = buildRequest();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return engine.renderInternal(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(new RenderFailedException(e.getMessage(), e));
            }
        }, engine.executor());
    }

    private CompositeRequest buildRequest() {
        return new CompositeRequest(children, layout, padding, scaleFactor, gridRows, gridCols);
    }

    @Override
    public CompositeRequest toInternalRequest() {
        return buildRequest();
    }
}
