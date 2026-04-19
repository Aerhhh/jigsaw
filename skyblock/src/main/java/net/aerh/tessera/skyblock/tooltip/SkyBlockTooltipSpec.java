package net.aerh.tessera.skyblock.tooltip;

import net.aerh.tessera.api.generator.RenderSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deferred-render tooltip specification emitted by {@link SkyBlockTooltipBuilder.Builder#build()}.
 *
 * <p>Implements {@link RenderSpec} so consumers can treat it polymorphically alongside
 * the api-level fluent builders. Carries the full set of tooltip parameters (lines, alpha,
 * padding, etc.) without referencing the internal Tessera {@code TooltipRequest} record
 * type:   that record is package-private to {@code tessera-core.generator}
 * and no longer nameable from the skyblock module.
 *
 * <p>Consumers wanting to actually render a {@code SkyBlockTooltipSpec} drive a live
 * Tessera engine like this:
 *
 * <pre>{@code
 * SkyBlockTooltipSpec spec = SkyBlockTooltipBuilder.builder()
 *     .name("Hyperion")
 *     .rarity(Rarity.byName("legendary").orElse(null))
 *     .lore("%%damage:500%%")
 *     .build();
 *
 * TooltipBuilder b = engine.tooltip()
 *     .lines(spec.lines())
 *     .alpha(spec.alpha())
 *     .padding(spec.padding())
 *     .firstLinePadding(spec.firstLinePadding())
 *     .maxLineLength(spec.maxLineLength())
 *     .centeredText(spec.centeredText())
 *     .renderBorder(spec.renderBorder())
 *     .scaleFactor(spec.scaleFactor());
 * GeneratorResult result = b.render();
 * }</pre>
 *
 * <p>The parameters in this record mirror the Tessera {@code TooltipRequest} record's
 * component list one-to-one so threading the values into a live engine is a field-by-field
 * copy with no translation.
 *
 * @param lines pre-formatted tooltip lines with {@code &} or {@code §} color codes;
 *                         already wrapped by {@link SkyBlockTooltipBuilder.Builder#buildLines()}
 *                         so the generator should not re-wrap (use {@code maxLineLength=0})
 * @param alpha background alpha value in {@code [0, 255]}
 * @param padding padding in pixels around the tooltip
 * @param firstLinePadding whether to add extra vertical padding below the first line
 * @param maxLineLength max visible characters per line before wrapping; {@code 0} disables
 *                         generator-level wrapping (the preferred setting here because lines
 *                         are pre-wrapped)
 * @param centeredText whether each line should be horizontally centered
 * @param renderBorder whether to render the Minecraft-style tooltip border
 * @param scaleFactor integer scale multiplier applied to all pixel coordinates
 */
public record SkyBlockTooltipSpec(
        List<String> lines,
        int alpha,
        int padding,
        boolean firstLinePadding,
        int maxLineLength,
        boolean centeredText,
        boolean renderBorder,
        int scaleFactor
) implements RenderSpec {

    public SkyBlockTooltipSpec {
        Objects.requireNonNull(lines, "lines must not be null");
        lines = Collections.unmodifiableList(new ArrayList<>(lines));
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("alpha must be between 0 and 255, got: " + alpha);
        }
        if (scaleFactor < 1) {
            throw new IllegalArgumentException("scaleFactor must be >= 1, got: " + scaleFactor);
        }
    }
}
