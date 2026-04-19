package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.cache.CacheKey;
import net.aerh.tessera.api.generator.RenderRequest;

/**
 * Package-private sealed interface for all built-in Tessera render requests.
 *
 * <p>Per decision, external consumer types extend {@link RenderRequest} directly
 * (via the {@code net.aerh.tessera.spi.GeneratorFactory} SPI) and are handled through
 * a {@code Class}-keyed fallback registry. Built-in types implement this sealed interface;
 * {@code DefaultEngine#renderInternal(CoreRenderRequest...)} pattern-matches exhaustively
 * so adding a seventh built-in without updating dispatch is a compile error.
 *
 * <p>Records can only implement interfaces (not extend classes), so the base is an
 * interface rather than an {@code abstract sealed class}; permitted shapes are identical
 * either way.
 *
 * <p>Each permitted record implements {@link #cacheKey()} directly. Record-component
 * {@code hashCode()} on {@code String}/{@code int}/{@code boolean}/{@code Optional} is
 * stable across JVM runs in Java 21, so the {@code Objects.hash(...)}-based content hash
 * in each record is cache-safe.
 */
public sealed interface CoreRenderRequest extends RenderRequest
        permits ItemRequest,
                TooltipRequest,
                InventoryRequest,
                PlayerHeadRequest,
                PlayerModelRequest,
                CompositeRequest {

    /**
     * Returns the cache key used by {@code CachingGenerator} for this request. Each permitted
     * record implements this by content-hashing its stable fields and passing the digest
     * through {@link CacheKey#of(RenderRequest, long)}, which stamps the current
     * {@link CacheKey#CACHE_KEY_VERSION} .
     */
    CacheKey cacheKey();

    /**
     * Returns a copy of this request with its scale factor overridden by {@code scaleFactor},
     * or {@code this} if the subtype does not use a scale field. Default: no-op returning
     * {@code this}.
     *
     * <p>Covariant refinement of {@link RenderRequest#withInheritedScale(int)} so that
     * composite fan-out can keep working on the internal sealed hierarchy without a cast.
     */
    @Override
    default CoreRenderRequest withInheritedScale(int scaleFactor) {
        return this;
    }
}
