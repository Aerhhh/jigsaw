package net.aerh.tessera.api.assets;

import com.sun.net.httpserver.HttpServer;
import net.aerh.tessera.api.assets.AssetEntry;
import net.aerh.tessera.api.assets.DownloadPipeline;
import net.aerh.tessera.api.exception.TesseraAssetDownloadException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lifecycle guarantees for the {@code.partial} file under two failure modes:
 * <ol>
 *   <li>HTTP error path: {@code.partial} is deleted (no orphans).</li>
 *   <li>Orphaned partial from a prior crash: next {@code download()} succeeds, overwriting
 *       whatever the partial contained, producing a correct final file.</li>
 * </ol>
 */
class PartialFileLifecycleTest {

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
    void httpErrorDoesNotLeaveOrphanedPartial(@TempDir Path cacheDir) {
        String sha1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String path = "/" + sha1.substring(0, 2) + "/" + sha1;

        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        AssetEntry entry = new AssetEntry(
                "minecraft/textures/block/leaks.png",
                sha1,
                100L,
                "http://127.0.0.1:" + port + path
        );

        DownloadPipeline pipeline = new DownloadPipeline();
        assertThatThrownBy(() -> pipeline.download(entry, cacheDir))
                .isInstanceOf(TesseraAssetDownloadException.class);

        Path target = cacheDir.resolve(entry.path());
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        assertThat(Files.exists(partial)).as("HTTP error must clean up .partial").isFalse();
    }

    @Test
    void leftoverPartialFromPreviousCrashIsOverwrittenOnNextDownload(@TempDir Path cacheDir)
            throws Exception {
        byte[] body = "good-bytes".getBytes();
        String sha1 = sha1Hex(body);
        String path = "/" + sha1.substring(0, 2) + "/" + sha1;

        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        AssetEntry entry = new AssetEntry(
                "minecraft/textures/block/resume.png",
                sha1,
                body.length,
                "http://127.0.0.1:" + port + path
        );

        // Simulate prior crash: write a truncated/garbage.partial at the target location.
        Path target = cacheDir.resolve(entry.path());
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        Files.createDirectories(target.getParent());
        Files.write(partial, "GARBAGE-FROM-PREVIOUS-CRASH".getBytes());

        DownloadPipeline pipeline = new DownloadPipeline();
        pipeline.download(entry, cacheDir);

        assertThat(Files.exists(target)).as("final file must exist").isTrue();
        assertThat(Files.readAllBytes(target)).isEqualTo(body);
        assertThat(Files.exists(partial)).as(".partial must be gone after successful download").isFalse();
    }

    static String sha1Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}
