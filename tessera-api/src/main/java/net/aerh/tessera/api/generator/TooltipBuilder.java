package net.aerh.tessera.api.generator;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Terminal-fluent builder for tooltip renders. Obtain via
 * {@link net.aerh.tessera.api.Engine#tooltip()}. Setter shapes lifted from the existing
 * {@code TooltipRequest.Builder}.
 *
 * <p>Per post-plan-check amendment, {@link #render()} and {@link #renderAsync()}
 * declare no checked throws; failures surface as the unchecked
 * {@link net.aerh.tessera.api.exception.RenderFailedException}.
 *
 * @see net.aerh.tessera.api.Engine#tooltip()
 */
public interface TooltipBuilder extends RenderSpec {

    TooltipBuilder line(String line);

    TooltipBuilder lines(List<String> lines);

    TooltipBuilder alpha(int alpha);

    TooltipBuilder padding(int padding);

    TooltipBuilder firstLinePadding(boolean firstLinePadding);

    TooltipBuilder maxLineLength(int maxLineLength);

    TooltipBuilder centeredText(boolean centeredText);

    TooltipBuilder renderBorder(boolean renderBorder);

    TooltipBuilder scaleFactor(int scaleFactor);

    TooltipBuilder context(GenerationContext context);

    GeneratorResult render();

    CompletableFuture<GeneratorResult> renderAsync();
}
