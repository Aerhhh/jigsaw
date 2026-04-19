package net.aerh.tessera.api.assets;

import com.sun.net.httpserver.HttpServer;
import net.aerh.tessera.api.assets.AssetEntry;
import net.aerh.tessera.api.assets.DownloadPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the cache-hit short-circuit behavior that makes {@code TesseraAssets.fetch(mcVer)}
 * idempotent: a warm cache never hits the network, and only a missing file triggers a
 * re-download.
 *
 * <p>Exercises the {@link DownloadPipeline} loop directly rather than through
 * {@code TesseraAssets.fetch(...)} - the classpath-manifest seam is covered by
 * {@code ManifestSchemaTest}; here the goal is to prove the idempotence contract
 * against a real HTTP server with hit counts per-entry.
 */
class TesseraAssetsIdempotenceTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void secondFetchOnWarmCacheDownloadsZeroFiles(@TempDir Path cacheDir) throws Exception {
        byte[] bodyA = "aaa".getBytes();
        byte[] bodyB = "bbbb".getBytes();
        String shaA = sha1Hex(bodyA);
        String shaB = sha1Hex(bodyB);

        AtomicInteger hitsA = new AtomicInteger();
        AtomicInteger hitsB = new AtomicInteger();
        registerOk("/" + shaA.substring(0, 2) + "/" + shaA, bodyA, hitsA);
        registerOk("/" + shaB.substring(0, 2) + "/" + shaB, bodyB, hitsB);

        AssetEntry a = new AssetEntry(
                "minecraft/textures/item/a.png", shaA, bodyA.length,
                "http://127.0.0.1:" + port + "/" + shaA.substring(0, 2) + "/" + shaA
        );
        AssetEntry b = new AssetEntry(
                "minecraft/textures/item/b.png", shaB, bodyB.length,
                "http://127.0.0.1:" + port + "/" + shaB.substring(0, 2) + "/" + shaB
        );

        DownloadPipeline pipeline = new DownloadPipeline();
        List<AssetEntry> manifest = List.of(a, b);

        // First pass - both downloaded.
        for (AssetEntry e : manifest) {
            pipeline.download(e, cacheDir);
        }
        assertThat(hitsA).hasValue(1);
        assertThat(hitsB).hasValue(1);

        // Second pass - zero network hits.
        for (AssetEntry e : manifest) {
            pipeline.download(e, cacheDir);
        }
        assertThat(hitsA).as("cache hit must not re-download a").hasValue(1);
        assertThat(hitsB).as("cache hit must not re-download b").hasValue(1);

        // Delete one, third pass - exactly one re-download.
        Files.delete(cacheDir.resolve(a.path()));
        for (AssetEntry e : manifest) {
            pipeline.download(e, cacheDir);
        }
        assertThat(hitsA).as("missing file must be re-downloaded").hasValue(2);
        assertThat(hitsB).as("present file must not re-download").hasValue(1);
    }

    private void registerOk(String path, byte[] body, AtomicInteger counter) {
        server.createContext(path, exchange -> {
            counter.incrementAndGet();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    static String sha1Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}
