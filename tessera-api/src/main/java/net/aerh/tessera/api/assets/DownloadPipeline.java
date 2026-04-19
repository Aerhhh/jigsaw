package net.aerh.tessera.api.assets;

import net.aerh.tessera.api.assets.AssetEntry;
import net.aerh.tessera.api.exception.TesseraAssetDownloadException;
import net.aerh.tessera.api.exception.TesseraAssetIntegrityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * HTTP + SHA1 + atomic-rename asset download pipeline. One instance per
 * {@link net.aerh.tessera.api.assets.TesseraAssets#fetch(String)} call; holds an
 * {@link HttpClient} configured with {@link ProxySelector#getDefault()} and a
 * virtual-thread executor.
 *
 * <p>Each download writes to a {@code <file>.partial} path, SHA1-verifies the bytes, and
 * only then performs a {@link StandardCopyOption#ATOMIC_MOVE} onto the final name. An
 * integrity mismatch throws {@link TesseraAssetIntegrityException} before the atomic move
 * runs, so the final path is never populated with corrupt bytes. Mid-download crashes leave
 * at most one orphaned {@code .partial} file that the next fetch overwrites.
 */
public final class DownloadPipeline {

    private static final Logger log = LoggerFactory.getLogger(DownloadPipeline.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    /**
     * Package-private constructor used by tests to inject a stub {@link HttpClient}.
     *
     * @param httpClient the HTTP client to use; must not be {@code null}
     */
    DownloadPipeline(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * Creates a pipeline backed by a default {@link HttpClient} with virtual-thread executor
     * and {@link ProxySelector#getDefault()}.
     */
    public DownloadPipeline() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .proxy(ProxySelector.getDefault())
                .build());
    }

    /**
     * Downloads {@code entry} into {@code cacheDir}. Idempotent: if the final file already exists
     * with the matching SHA1, returns without touching the network.
     *
     * @param entry the manifest entry to fetch; must not be {@code null}
     * @param cacheDir the per-MC-version cache directory; must not be {@code null}
     * @throws TesseraAssetIntegrityException if the downloaded bytes' SHA1 does not match, or if
     *                                        the entry's path escapes the cache directory
     * @throws TesseraAssetDownloadException if the HTTP GET fails or returns non-200
     */
    public void download(AssetEntry entry, Path cacheDir)
            throws TesseraAssetDownloadException, TesseraAssetIntegrityException {
        Objects.requireNonNull(entry, "entry must not be null");
        Objects.requireNonNull(cacheDir, "cacheDir must not be null");

        Path cacheDirNormalized = cacheDir.normalize();
        Path target = cacheDirNormalized.resolve(entry.path()).normalize();

        // Reject manifest paths that escape the cache dir
        if (!target.startsWith(cacheDirNormalized)) {
            throw new TesseraAssetIntegrityException(
                    "Manifest path escapes cache dir: " + entry.path(),
                    Map.of("path", entry.path(), "cacheDir", cacheDir.toString())
            );
        }

        // Cache-hit short-circuit: existing file with matching SHA1 is a no-op
        if (Files.exists(target)) {
            String existingSha = sha1Hex(target);
            if (existingSha.equalsIgnoreCase(entry.sha1())) {
                log.debug("Cache hit for {} ({})", entry.path(), entry.sha1());
                return;
            }
            log.warn("Cache entry for {} has SHA1 {} but manifest expects {}; re-downloading",
                    entry.path(), existingSha, entry.sha1());
        }

        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new TesseraAssetDownloadException(
                    "Failed to create cache directory for " + entry.path(),
                    Map.of("path", entry.path(), "cacheDir", cacheDir.toString()),
                    e
            );
        }
        // BodyHandlers.ofFile(path) opens CREATE|WRITE without TRUNCATE_EXISTING, so a
        // leftover.partial from a prior crash would overlay new bytes on top of old bytes
        // (keeping any trailing old bytes past the new body length). Delete first for a
        // clean write.
        deleteQuietly(partial);

        HttpRequest req = HttpRequest.newBuilder(URI.create(entry.url()))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<Path> resp;
        try {
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(partial));
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new TesseraAssetDownloadException(
                    "Failed to download " + entry.path(),
                    Map.of("url", entry.url(), "path", entry.path()),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(partial);
            throw new TesseraAssetDownloadException(
                    "Download interrupted for " + entry.path(),
                    Map.of("url", entry.url(), "path", entry.path()),
                    e
            );
        }

        if (resp.statusCode() != 200) {
            deleteQuietly(partial);
            throw new TesseraAssetDownloadException(
                    "HTTP " + resp.statusCode() + " for " + entry.url(),
                    Map.of(
                            "url", entry.url(),
                            "path", entry.path(),
                            "statusCode", resp.statusCode()
                    )
            );
        }

        String actualSha1 = sha1Hex(partial);
        if (!actualSha1.equalsIgnoreCase(entry.sha1())) {
            deleteQuietly(partial);
            throw new TesseraAssetIntegrityException(entry.path(), entry.sha1(), actualSha1);
        }

        try {
            Files.move(partial, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new TesseraAssetDownloadException(
                    "Atomic rename failed for " + entry.path(),
                    Map.of("path", entry.path()),
                    e
            );
        }
        log.debug("Downloaded {} ({} bytes, sha1 {})", entry.path(), entry.size(), entry.sha1());
    }

    /**
     * Computes the SHA-1 hex digest of the given file. Package-private for tests.
     *
     * @param file the file to hash
     * @return lowercase 40-char hex SHA-1 digest
     */
    static String sha1Hex(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (var in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("SHA-1 hashing failed for " + file, e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort cleanup; nothing useful to do on failure
        }
    }

    /**
     * Returns the underlying {@link HttpClient}. Package-private for
     * {@code DownloadPipelineProxyTest}.
     *
     * @return the HTTP client this pipeline was configured with
     */
    HttpClient httpClient() {
        return httpClient;
    }
}
