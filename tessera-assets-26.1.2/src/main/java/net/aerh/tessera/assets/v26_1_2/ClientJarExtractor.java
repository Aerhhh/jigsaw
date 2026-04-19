package net.aerh.tessera.assets.v26_1_2;

import net.aerh.tessera.api.exception.TesseraAssetIntegrityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Downloads and extracts the Mojang client.jar for Minecraft 26.1.2 (and peers) into the
 * on-disk asset cache Tessera's render pipeline reads from.
 *
 * <p>piston-meta's {@code objects} list does NOT include item/block textures
 * because Mojang bundles those inside {@code client.jar} post-1.19. This extractor closes
 * that gap so every render path has a populated texture tree on disk.
 *
 * <p>Hardening:
 * <ul>
 *   <li>SHA-1 verified against the caller-supplied expected digest (from piston-meta's
 *       {@code downloads.client.sha1}). Mismatch throws
 *       {@link TesseraAssetIntegrityException}   (reused, not redefined).</li>
 *   <li>Double-layered zip-slip guard: each entry's resolved absolute path is checked to start
 *       with the normalised extraction root, AND entry names with {@code..} segments or
 *       leading slashes are explicitly rejected.</li>
 *   <li>Deterministic extraction order: entries are sorted by name before write so repeat runs
 *       on the same jar bytes produce byte-identical output trees.</li>
 *   <li>Temp file + best-effort delete on any failure: no half-written jar on disk after
 *       SHA-1 failure.</li>
 * </ul>
 */
public final class ClientJarExtractor {

    private static final Logger log = LoggerFactory.getLogger(ClientJarExtractor.class);

    /**
     * Asset roots inside {@code client.jar} that Tessera's render pipeline reads. Anything not
     * matching these prefixes (or equalling a required file) is ignored at extraction time.
     */
    static final List<String> REQUIRED_PREFIXES = List.of(
            "assets/minecraft/textures/",
            "assets/minecraft/models/",
            "assets/minecraft/font/"
    );

    /** Required files (exact-match paths inside the jar). */
    static final List<String> REQUIRED_FILES = List.of(
            "assets/minecraft/lang/en_us.json",
            "pack.mcmeta"
    );

    private ClientJarExtractor() {
        /* static-only */
    }

    /**
     * Downloads {@code clientJarUrl}, verifies its SHA-1 against {@code expectedSha1}, and
     * extracts the required asset roots into {@code destRoot}.
     *
     * @param clientJarUrl the absolute URL to fetch client.jar from (typically
     *                     {@code piston-data.mojang.com/v1/objects/<sha1>/client.jar})
     * @param expectedSha1 the 40-char lowercase SHA-1 digest declared in piston-meta's
     *                     {@code downloads.client.sha1}
     * @param destRoot the on-disk asset cache root (e.g. {@code ~/.tessera/assets/26.1.2/})
     * @param httpClient the HTTP client to drive the download (typically the shared
     *                     {@link net.aerh.tessera.api.assets.DownloadPipeline} client)
     * @throws TesseraAssetIntegrityException if the HTTP response is non-200, the downloaded
     *                                        SHA-1 does not match, or any entry escapes
     *                                        {@code destRoot}
     * @throws IOException if the network IO or extraction IO fails for
     *                                        non-integrity reasons (e.g. disk full)
     * @throws InterruptedException if the HTTP send is interrupted
     * @throws NullPointerException if any argument is {@code null}
     */
    public static void extract(URI clientJarUrl, String expectedSha1, Path destRoot,
                               HttpClient httpClient)
            throws TesseraAssetIntegrityException, IOException, InterruptedException {
        Objects.requireNonNull(clientJarUrl, "clientJarUrl must not be null");
        Objects.requireNonNull(expectedSha1, "expectedSha1 must not be null");
        Objects.requireNonNull(destRoot, "destRoot must not be null");
        Objects.requireNonNull(httpClient, "httpClient must not be null");

        Path tempJar = Files.createTempFile("tessera-client-", ".jar");
        try {
            downloadAndVerify(clientJarUrl, expectedSha1, tempJar, httpClient);
            extractTo(tempJar, destRoot);
        } finally {
            try {
                Files.deleteIfExists(tempJar);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void downloadAndVerify(URI clientJarUrl, String expectedSha1, Path tempJar,
                                          HttpClient httpClient)
            throws TesseraAssetIntegrityException, IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(clientJarUrl).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            // Consume and close the body so no socket is leaked on the non-200 path.
            try (InputStream drain = response.body()) {
                drain.transferTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // body stream already abandoned; fall through to the integrity error
            }
            throw new TesseraAssetIntegrityException(
                    "HTTP " + response.statusCode() + " fetching client.jar from " + clientJarUrl);
        }

        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is a mandatory MessageDigest algorithm in every conformant JRE.
            throw new IllegalStateException("SHA-1 digest unavailable", e);
        }

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tempJar,
                     StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                sha1.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }

        String actualSha1 = HexFormat.of().formatHex(sha1.digest());
        if (!actualSha1.equalsIgnoreCase(expectedSha1)) {
            throw new TesseraAssetIntegrityException(String.format(
                    "client.jar SHA1 mismatch: expected=%s actual=%s url=%s",
                    expectedSha1, actualSha1, clientJarUrl));
        }
        log.debug("client.jar SHA1 verified: {} ({} bytes at {})",
                actualSha1, Files.size(tempJar), tempJar);
    }

    /**
     * Extracts the required roots from {@code jarFile} into {@code destRoot}. Deterministic:
     * entries are sorted by name; each entry is checked against the zip-slip guard before any
     * bytes are written.
     */
    private static void extractTo(Path jarFile, Path destRoot)
            throws TesseraAssetIntegrityException, IOException {
        Path resolvedDest = destRoot.toAbsolutePath().normalize();
        Files.createDirectories(resolvedDest);

        int extracted = 0;
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            List<JarEntry> entries = jar.stream()
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (!isRequired(entry.getName())) {
                    continue;
                }
                String name = entry.getName();

                // Zip-slip + absolute-path guard (belt-and-braces):
                //   1. explicit `..` / leading-slash rejection on the raw name
                //   2. normalised candidate path must stay inside resolvedDest
                if (name.startsWith("/") || name.contains("..")
                        || name.contains("\\") || name.contains(":")) {
                    throw new TesseraAssetIntegrityException(
                            "Unsafe jar entry path rejected: " + name);
                }
                Path candidate = resolvedDest.resolve(name).normalize();
                if (!candidate.startsWith(resolvedDest)) {
                    throw new TesseraAssetIntegrityException(
                            "Zip-slip rejected: entry '" + name + "' escapes " + resolvedDest);
                }

                Path parent = candidate.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, candidate, StandardCopyOption.REPLACE_EXISTING);
                }
                extracted++;
            }
        }
        log.info("client.jar extraction complete: {} entries written under {}", extracted, resolvedDest);
    }

    private static boolean isRequired(String entryName) {
        for (String prefix : REQUIRED_PREFIXES) {
            if (entryName.startsWith(prefix)) {
                return true;
            }
        }
        return REQUIRED_FILES.contains(entryName);
    }
}
