package net.aerh.tessera.api.cache;

import net.aerh.tessera.api.generator.RenderRequest;

import java.util.Objects;

/**
 * Compact, stable cache key for a render request result. Used by {@code CachingGenerator}
 * in {@code tessera-core} and by external {@code GeneratorFactory} consumers that opt
 * into caching via {@link net.aerh.tessera.spi.GeneratorFactory#keyFunction()}.
 *
 * @param requestType the concrete class of the request (e.g. {@code ItemRequest.class})
 * @param version stamp from {@link #CACHE_KEY_VERSION} - bumping invalidates cached results
 * @param contentHash 64-bit digest of the request's cache-relevant fields
 */
public record CacheKey(Class<? extends RenderRequest> requestType, int version, long contentHash) {

    /**
     * Global cache-key version. Bump whenever a request shape or generator output format
     * changes in a cache-semantically-breaking way. Bumping invalidates all cached
     * results at engine startup (Caffeine caches rebuild from empty on the next
     * {@code Engine.builder().build()}).
     *
     * <p>Version history (date - bump rationale):
     * <ul>
     *   <li>{@code 1} - 2026-04-18 - initial release (Tessera 1.0.0).</li>
     * </ul>
     */
    public static final int CACHE_KEY_VERSION = 1;

    public CacheKey {
        Objects.requireNonNull(requestType, "requestType must not be null");
    }

    /**
     * Convenience factory stamping the current {@link #CACHE_KEY_VERSION}.
     *
     * @param request the request whose {@link Class} becomes the key's {@code requestType}
     * @param contentHash caller-computed 64-bit digest of the request's cache-relevant fields
     * @return a {@code CacheKey} with {@code version == CACHE_KEY_VERSION}
     * @throws NullPointerException if {@code request} is null
     */
    public static CacheKey of(RenderRequest request, long contentHash) {
        Objects.requireNonNull(request, "request must not be null");
        return new CacheKey(request.getClass(), CACHE_KEY_VERSION, contentHash);
    }
}
