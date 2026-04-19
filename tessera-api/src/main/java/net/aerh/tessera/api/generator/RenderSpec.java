package net.aerh.tessera.api.generator;

/**
 * Public marker interface for everything consumers can pass to
 * {@link net.aerh.tessera.api.Engine}'s fluent composite / fromNbt entry points.
 *
 * <p>All seven fluent builders ({@link ItemBuilder}, {@link TooltipBuilder}...) extend
 * {@code RenderSpec}, so consumers can freely compose:
 *
 * <pre>{@code
 * engine.composite().horizontal()
 *     .add(engine.item().itemId("diamond_sword"))
 *     .add(engine.tooltip().line("Legendary"))
 *     .render();
 * }</pre>
 *
 * <p>{@code RenderSpec} is intentionally distinct from {@link RenderRequest}.
 * {@code RenderRequest} is the internal SPI marker for external-{@code GeneratorFactory}
 * consumer plugin points; {@code RenderSpec} is the public composition marker.
 */
public interface RenderSpec {
}
