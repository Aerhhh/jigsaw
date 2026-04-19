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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy-path: 200 OK with body matching the manifest SHA1 produces a final file on disk and
 * deletes the {@code.partial} sibling.
 */
class DownloadPipelineTest {

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
    void happyPathWritesFinalFileAndDeletesPartial(@TempDir Path cacheDir) throws Exception {
        byte[] body = "the-content-bytes".getBytes();
        String sha1 = sha1Hex(body);
        String path = "/" + sha1.substring(0, 2) + "/" + sha1;

        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        AssetEntry entry = new AssetEntry(
                "minecraft/textures/block/diamond_ore.png",
                sha1,
                body.length,
                "http://127.0.0.1:" + port + path
        );

        DownloadPipeline pipeline = new DownloadPipeline();
        pipeline.download(entry, cacheDir);

        Path target = cacheDir.resolve(entry.path());
        assertThat(Files.exists(target)).as("final file must exist").isTrue();
        assertThat(Files.readAllBytes(target)).isEqualTo(body);
        assertThat(Files.exists(target.resolveSibling(target.getFileName() + ".partial")))
                .as(".partial must be removed after atomic rename").isFalse();
    }

    @Test
    void existingFileWithMatchingSha1IsCacheHitAndDoesNotHitNetwork(@TempDir Path cacheDir)
            throws Exception {
        byte[] body = "already-cached".getBytes();
        String sha1 = sha1Hex(body);

        Path target = cacheDir.resolve("minecraft/textures/block/stone.png");
        Files.createDirectories(target.getParent());
        Files.write(target, body);

        AtomicInteger hits = new AtomicInteger();
        String path = "/" + sha1.substring(0, 2) + "/" + sha1;
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        AssetEntry entry = new AssetEntry(
                "minecraft/textures/block/stone.png",
                sha1,
                body.length,
                "http://127.0.0.1:" + port + path
        );

        DownloadPipeline pipeline = new DownloadPipeline();
        pipeline.download(entry, cacheDir); // must not throw

        assertThat(hits.get()).as("cache hit must not invoke HTTP").isZero();
        assertThat(Files.readAllBytes(target)).isEqualTo(body);
    }

    static String sha1Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}
