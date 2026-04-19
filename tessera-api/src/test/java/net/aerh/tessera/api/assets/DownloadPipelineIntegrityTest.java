package net.aerh.tessera.api.assets;

import com.sun.net.httpserver.HttpServer;
import net.aerh.tessera.api.assets.AssetEntry;
import net.aerh.tessera.api.assets.DownloadPipeline;
import net.aerh.tessera.api.exception.TesseraAssetIntegrityException;
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
 * SHA1 mismatch: server returns 200 with bytes whose SHA1 does NOT match the manifest entry.
 * Expect {@link TesseraAssetIntegrityException} with a context map naming path, expectedSha1,
 * actualSha1; the {@code.partial} file must be deleted.
 */
class DownloadPipelineIntegrityTest {

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
    @SuppressWarnings("unchecked")
    void sha1MismatchThrowsIntegrityExceptionAndDeletesPartial(@TempDir Path cacheDir)
            throws Exception {
        byte[] actualBody = "UNEXPECTED-BYTES".getBytes();
        String actualSha1 = sha1Hex(actualBody);

        // Manifest claims a different SHA1 (arbitrary valid 40-hex-lowercase string).
        String expectedSha1 = "0000000000000000000000000000000000000000";
        String path = "/" + expectedSha1.substring(0, 2) + "/" + expectedSha1;

        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(200, actualBody.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(actualBody);
            }
        });

        AssetEntry entry = new AssetEntry(
                "minecraft/textures/block/diamond_ore.png",
                expectedSha1,
                actualBody.length,
                "http://127.0.0.1:" + port + path
        );

        DownloadPipeline pipeline = new DownloadPipeline();

        assertThatThrownBy(() -> pipeline.download(entry, cacheDir))
                .isInstanceOf(TesseraAssetIntegrityException.class)
                .hasMessageContaining(entry.path())
                .hasMessageContaining(expectedSha1)
                .hasMessageContaining(actualSha1)
                .satisfies(ex -> {
                    var context = ((TesseraAssetIntegrityException) ex).getContext();
                    assertThat(context).containsEntry("path", entry.path());
                    assertThat(context).containsEntry("expectedSha1", expectedSha1);
                    assertThat(context).containsEntry("actualSha1", actualSha1);
                });

        Path target = cacheDir.resolve(entry.path());
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        assertThat(Files.exists(target)).as("final file must not exist after SHA1 mismatch").isFalse();
        assertThat(Files.exists(partial)).as(".partial must be deleted after SHA1 mismatch").isFalse();
    }

    static String sha1Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}
