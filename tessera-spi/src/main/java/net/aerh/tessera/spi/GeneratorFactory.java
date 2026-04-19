package net.aerh.tessera.spi;

import net.aerh.tessera.api.cache.CacheKey;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorType;

import java.util.Optional;
import java.util.function.Function;

/**
 * SPI contract for contributing a {@link Generator} implementation.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} or explicit registration.
 *
 * @param <I> input type consumed by the produced {@link Generator}
 * @param <O> output type produced by the produced {@link Generator}
 *
 * @see net.aerh.tessera.api.generator.Generator
 * @see net.aerh.tessera.api.generator.GeneratorType
 */
public interface GeneratorFactory<I, O> {

    /**
     * Unique identifier for this factory (e.g. {@code "mig:item"}).
     */
    String id();

    /**
     * The type of generator produced by this factory.
     */
    GeneratorType type();

    /**
     * Creates and returns a new generator instance.
     */
    Generator<I, O> create();

    /**
     * Optionally opts this factory into result caching via
     * {@code net.aerh.tessera.core.cache.CachingGenerator}. Default returns
     * {@link Optional#empty()} - generators run uncached.
     *
     * <p>Per decision, when a non-empty function is returned, {@code CachingGenerator}
     * wraps the produced {@link Generator}. Each produced {@link CacheKey} carries the
     * current {@link CacheKey#CACHE_KEY_VERSION} so version bumps invalidate all cached
     * entries at engine startup.
     *
     * @return an {@code Optional} containing a content-hash-producing function, or empty
     *         to indicate uncached operation
     */
    default Optional<Function<I, CacheKey>> keyFunction() {
        return Optional.empty();
    }
}
