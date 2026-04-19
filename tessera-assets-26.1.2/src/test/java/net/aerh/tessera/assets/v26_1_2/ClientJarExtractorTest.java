package net.aerh.tessera.assets.v26_1_2;

import net.aerh.tessera.api.exception.TesseraAssetIntegrityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RED-first TDD coverage for {@link ClientJarExtractor}.
 *
 * <p>Fixtures are built programmatically in each test so no binary files are committed to git.
 * The {@link HttpClient} is stubbed via an in-memory subclass so no network is hit.
 */
class ClientJarExtractorTest {

    private static final String VALID_SHA1_PLACEHOLDER = "0000000000000000000000000000000000000000";

    @Test
    void extract_valid_jar_writes_required_paths_and_skips_non_required(@TempDir Path tmp) throws Exception {
        byte[] jarBytes = buildValidJar();
        String sha1 = sha1Hex(jarBytes);
        Path destRoot = tmp.resolve("cache");
        HttpClient stub = new StubHttpClient(jarBytes, 200);

        ClientJarExtractor.extract(URI.create("https://example.test/client.jar"),
                sha1, destRoot, stub);

        // Required paths exist.
        assertThat(Files.exists(destRoot.resolve("pack.mcmeta"))).isTrue();
        assertThat(Files.exists(destRoot.resolve("assets/minecraft/textures/item/apple.png"))).isTrue();
        assertThat(Files.exists(destRoot.resolve("assets/minecraft/lang/en_us.json"))).isTrue();

        // Non-required path NOT extracted.
        assertThat(Files.exists(destRoot.resolve("some/random/path.dat"))).isFalse();
    }

    @Test
    void extract_with_mismatched_sha1_throws_integrity_exception(@TempDir Path tmp) throws Exception {
        byte[] jarBytes = buildValidJar();
        Path destRoot = tmp.resolve("cache");
        HttpClient stub = new StubHttpClient(jarBytes, 200);

        assertThatThrownBy(() -> ClientJarExtractor.extract(
                URI.create("https://example.test/client.jar"),
                "deadbeef" + "0".repeat(32),
                destRoot,
                stub))
                .isInstanceOf(TesseraAssetIntegrityException.class)
                .hasMessageContaining("SHA1 mismatch");

        // No extraction happened.
        assertThat(Files.exists(destRoot.resolve("pack.mcmeta"))).isFalse();
    }

    @Test
    void extract_with_zipslip_entry_throws_integrity_exception(@TempDir Path tmp) throws Exception {
        byte[] jarBytes = buildZipSlipJar();
        String sha1 = sha1Hex(jarBytes);
        Path destRoot = tmp.resolve("cache");
        HttpClient stub = new StubHttpClient(jarBytes, 200);

        assertThatThrownBy(() -> ClientJarExtractor.extract(
                URI.create("https://example.test/client.jar"),
                sha1, destRoot, stub))
                .isInstanceOf(TesseraAssetIntegrityException.class);
    }

    @Test
    void extract_into_existing_cache_overwrites_cleanly(@TempDir Path tmp) throws Exception {
        byte[] jarBytes = buildValidJar();
        String sha1 = sha1Hex(jarBytes);
        Path destRoot = tmp.resolve("cache");

        ClientJarExtractor.extract(URI.create("https://example.test/client.jar"),
                sha1, destRoot, new StubHttpClient(jarBytes, 200));
        byte[] firstRun = Files.readAllBytes(destRoot.resolve("assets/minecraft/textures/item/apple.png"));

        ClientJarExtractor.extract(URI.create("https://example.test/client.jar"),
                sha1, destRoot, new StubHttpClient(jarBytes, 200));
        byte[] secondRun = Files.readAllBytes(destRoot.resolve("assets/minecraft/textures/item/apple.png"));

        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    void extract_closes_http_response_stream_on_success(@TempDir Path tmp) throws Exception {
        byte[] jarBytes = buildValidJar();
        String sha1 = sha1Hex(jarBytes);
        Path destRoot = tmp.resolve("cache");
        AtomicInteger closeCount = new AtomicInteger();
        HttpClient stub = new StubHttpClient(jarBytes, 200, closeCount);

        ClientJarExtractor.extract(URI.create("https://example.test/client.jar"),
                sha1, destRoot, stub);

        assertThat(closeCount.get()).as("response stream closed after success").isGreaterThanOrEqualTo(1);
    }

    @Test
    void extract_closes_http_response_stream_on_sha1_failure(@TempDir Path tmp) throws Exception {
        byte[] jarBytes = buildValidJar();
        Path destRoot = tmp.resolve("cache");
        AtomicInteger closeCount = new AtomicInteger();
        HttpClient stub = new StubHttpClient(jarBytes, 200, closeCount);

        assertThatThrownBy(() -> ClientJarExtractor.extract(
                URI.create("https://example.test/client.jar"),
                VALID_SHA1_PLACEHOLDER,
                destRoot,
                stub))
                .isInstanceOf(TesseraAssetIntegrityException.class);

        assertThat(closeCount.get()).as("response stream closed even on sha1 failure").isGreaterThanOrEqualTo(1);
    }

    @Test
    void extract_absolute_path_entry_rejected(@TempDir Path tmp) throws Exception {
        byte[] jarBytes = buildAbsolutePathJar();
        String sha1 = sha1Hex(jarBytes);
        Path destRoot = tmp.resolve("cache");
        HttpClient stub = new StubHttpClient(jarBytes, 200);

        assertThatThrownBy(() -> ClientJarExtractor.extract(
                URI.create("https://example.test/client.jar"),
                sha1, destRoot, stub))
                .isInstanceOf(TesseraAssetIntegrityException.class);
    }

    @Test
    void extract_non_200_response_throws_integrity(@TempDir Path tmp) throws Exception {
        Path destRoot = tmp.resolve("cache");
        HttpClient stub = new StubHttpClient(new byte[0], 404);

        assertThatThrownBy(() -> ClientJarExtractor.extract(
                URI.create("https://example.test/client.jar"),
                VALID_SHA1_PLACEHOLDER, destRoot, stub))
                .isInstanceOf(TesseraAssetIntegrityException.class)
                .hasMessageContaining("404");
    }

    // ---- fixture builders ----

    private static byte[] buildValidJar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(baos)) {
            addEntry(out, "pack.mcmeta", "{\"pack\":{\"pack_format\":84}}".getBytes());
            addEntry(out, "assets/minecraft/textures/item/apple.png", syntheticPng());
            addEntry(out, "assets/minecraft/lang/en_us.json", "{\"item.minecraft.apple\":\"Apple\"}".getBytes());
            addEntry(out, "some/random/path.dat", "ignore".getBytes());
        }
        return baos.toByteArray();
    }

    private static byte[] buildZipSlipJar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(baos)) {
            // Name inside a required prefix so the required-path gate lets it through, forcing the
            // zip-slip guard to fire instead.
            addEntry(out, "assets/minecraft/textures/../../../../etc/passwd", "bad".getBytes());
        }
        return baos.toByteArray();
    }

    private static byte[] buildAbsolutePathJar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(baos)) {
            // Backslash-embedded entry name inside a required prefix. On a POSIX-style JDK the
            // guard should reject the backslash unconditionally (Windows file-separator, never
            // valid inside a zip entry).
            addEntry(out, "assets/minecraft/textures/evil\\..\\..\\passwd", "bad".getBytes());
        }
        return baos.toByteArray();
    }

    private static void addEntry(JarOutputStream out, String name, byte[] body) throws IOException {
        JarEntry entry = new JarEntry(name);
        out.putNextEntry(entry);
        out.write(body);
        out.closeEntry();
    }

    private static byte[] syntheticPng() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000);
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static String sha1Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(bytes));
    }

    // ---- stub HttpClient ----

    /**
     * Minimal {@link HttpClient} subclass that returns a fixed body + status for
     * {@code send(request, BodyHandlers.ofInputStream())}. The response's InputStream wraps the
     * supplied byte[] and increments {@link #closeCount} on {@link InputStream#close()}.
     */
    private static final class StubHttpClient extends HttpClient {
        private final byte[] body;
        private final int statusCode;
        private final AtomicInteger closeCount;

        StubHttpClient(byte[] body, int statusCode) {
            this(body, statusCode, new AtomicInteger());
        }

        StubHttpClient(byte[] body, int statusCode, AtomicInteger closeCount) {
            this.body = body;
            this.statusCode = statusCode;
            this.closeCount = closeCount;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> HttpResponse<T> send(HttpRequest request,
                                        HttpResponse.BodyHandler<T> responseBodyHandler) {
            InputStream in = new java.io.ByteArrayInputStream(body) {
                @Override public void close() throws IOException {
                    closeCount.incrementAndGet();
                    super.close();
                }
            };
            return (HttpResponse<T>) new StubResponse(in, statusCode, request);
        }

        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
        @Override public java.util.Optional<java.time.Duration> connectTimeout() { return java.util.Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() {
            try { return javax.net.ssl.SSLContext.getDefault(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }
    }

    private record StubResponse(InputStream body, int statusCode, HttpRequest request)
            implements HttpResponse<InputStream> {
        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return request; }
        @Override public java.util.Optional<HttpResponse<InputStream>> previousResponse() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }
        @Override public InputStream body() { return body; }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
        @Override public java.net.URI uri() { return request.uri(); }
        @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
        // Silence unused parameter warnings
        @SuppressWarnings("unused") private void noopReferenceToOutputStream(OutputStream ignored) {}
    }
}
