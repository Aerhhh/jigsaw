package net.aerh.tessera.api.assets;

import net.aerh.tessera.api.assets.DownloadPipeline;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.ProxySelector;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts: the default {@link HttpClient} built by {@link DownloadPipeline} wires up
 * {@link ProxySelector#getDefault()} so corporate-proxy-aware environments keep working.
 *
 * <p>Reaches into the pipeline's package-private {@code httpClient()} accessor via reflection
 * to avoid exposing {@code HttpClient} on the public API.
 */
class DownloadPipelineProxyTest {

    @Test
    void defaultHttpClientUsesDefaultProxySelector() throws Exception {
        DownloadPipeline pipeline = new DownloadPipeline();

        Method accessor = DownloadPipeline.class.getDeclaredMethod("httpClient");
        accessor.setAccessible(true);
        HttpClient client = (HttpClient) accessor.invoke(pipeline);

        assertThat(client.proxy())
                .as("HttpClient must carry a ProxySelector")
                .isPresent()
                .hasValue(ProxySelector.getDefault());
    }
}
