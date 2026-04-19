package net.aerh.tessera.spi;

import net.aerh.tessera.api.font.FontProvider;

/**
 * SPI contract for contributing a {@link FontProvider} to the font registry.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} or explicit registration.
 *
 * @see net.aerh.tessera.api.font.FontProvider
 */
public interface FontProviderFactory {

    /**
     * Unique identifier for the font provider produced by this factory (e.g. {@code "minecraft:default"}).
     */
    String id();

    /**
     * Creates and returns a new font provider instance.
     */
    FontProvider create();
}
