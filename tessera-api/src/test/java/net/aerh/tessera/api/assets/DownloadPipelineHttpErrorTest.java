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
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 404 / 5xx: non-200 response throws {@link TesseraAssetDownloadException} with context map
 * keys {@code url}, {@code path}, {@code statusCode}. The {@code.partial} file is deleted.
 */
class DownloadPipelineHttpErrorTest {

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
    void nonOkStatusThrowsDownloadExceptionAndDeletesPartial(@TempDir Path cacheDir) {
        String sha1 = "0123456789abcdef0123456789abcdef01234567";
        String path = "/" + sha1.substring(0, 2) + "/" + sha1;

        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        AssetEntry entry = new AssetEntry(
                "minecraft/textures/item/missing.png",
                sha1,
                1234L,
                "http://127.0.0.1:" + port + path
        );

        DownloadPipeline pipeline = new DownloadPipeline();

        assertThatThrownBy(() -> pipeline.download(entry, cacheDir))
                .isInstanceOf(TesseraAssetDownloadException.class)
                .hasMessageContaining("404")
                .hasMessageContaining(entry.url())
                .satisfies(ex -> {
                    var context = ((TesseraAssetDownloadException) ex).getContext();
                    assertThat(context).containsEntry("url", entry.url());
                    assertThat(context).containsEntry("path", entry.path());
                    assertThat(context).containsEntry("statusCode", 404);
                });

        Path target = cacheDir.resolve(entry.path());
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        assertThat(Files.exists(target)).as("final file absent after HTTP error").isFalse();
        assertThat(Files.exists(partial)).as(".partial must be deleted after HTTP error").isFalse();
    }
}
