package net.aerh.tessera.api.cache;

import net.aerh.tessera.api.generator.RenderRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies  + : the CACHE_KEY_VERSION constant is the global cache version stamp,
 * the {@link CacheKey#of(RenderRequest, long)} factory stamps it on every produced key, and
 * the record honors all three components in {@code equals}/{@code hashCode}.
 */
class CacheKeyContractTest {

    record TestRequest() implements RenderRequest {
    }

    @Test
    void cacheKeyVersionIsCurrent() {
        //  intro release stamps version 1; bumping invalidates every cached entry on
        // engine startup. Update this assertion alongside any future bump.
        assertThat(CacheKey.CACHE_KEY_VERSION).isEqualTo(1);
    }

    @Test
    void factoryOfStampsCurrentVersion() {
        CacheKey key = CacheKey.of(new TestRequest(), 0x12345678L);
        assertThat(key.version()).isEqualTo(CacheKey.CACHE_KEY_VERSION);
        assertThat(key.contentHash()).isEqualTo(0x12345678L);
        assertThat(key.requestType()).isEqualTo(TestRequest.class);
    }

    @Test
    void constructorRejectsNullRequestType() {
        assertThrows(NullPointerException.class, () -> new CacheKey(null, 1, 0L));
    }

    @Test
    void factoryRejectsNullRequest() {
        assertThrows(NullPointerException.class, () -> CacheKey.of(null, 1L));
    }

    @Test
    void equalsAndHashCodeHonourAllThreeComponents() {
        CacheKey a = new CacheKey(TestRequest.class, 1, 42L);
        CacheKey b = new CacheKey(TestRequest.class, 1, 42L);
        CacheKey differentHash = new CacheKey(TestRequest.class, 1, 43L);
        CacheKey differentVersion = new CacheKey(TestRequest.class, 2, 42L);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(differentHash);
        assertThat(a).isNotEqualTo(differentVersion);
    }
}
