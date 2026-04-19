package net.aerh.tessera.core.resource;

import net.aerh.tessera.api.exception.ResourcePackException;
import net.aerh.tessera.api.resource.PackMetadata;
import net.aerh.tessera.api.resource.ResourcePack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link ResourcePack} backed by a zip file.
 *
 * <p>Zip-slip hardening: the constructor fail-fast scans every entry for zip-slip patterns
 * ({@code..} segments or absolute paths) and throws {@link ResourcePackException} if any
 * malicious entry is detected. {@link #getResource} also double-guards against
 * caller-supplied traversal paths.
 */
public class ZipResourcePack implements ResourcePack {

    private final ZipFile zipFile;
    private final PackMetadata metadata;

    public ZipResourcePack(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        ZipFile opened;
        try {
            opened = new ZipFile(path.toFile());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to open zip file: " + path, e);
        }
        // Fail-fast scan of every entry. If any entry name contains a zip-slip pattern
        // the pack is refused before it's ever usable.
        try {
            scanForUnsafeEntries(opened);
        } catch (ResourcePackException e) {
            closeQuietly(opened, e);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(opened, e);
            throw e;
        }
        this.zipFile = opened;
        try {
            this.metadata = parseMetadata();
        } catch (RuntimeException e) {
            closeQuietly(opened, e);
            throw e;
        }
    }

    @Override
    public Optional<InputStream> getResource(String path) {
        Objects.requireNonNull(path, "path must not be null");
        // Double-guard: the caller's requested path must not itself be a traversal attempt.
        assertSafeRelativePath(path);

        ZipEntry entry = zipFile.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            return Optional.empty();
        }
        try {
            byte[] data = zipFile.getInputStream(entry).readAllBytes();
            return Optional.of(new ByteArrayInputStream(data));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasResource(String path) {
        ZipEntry entry = zipFile.getEntry(path);
        return entry != null && !entry.isDirectory();
    }

    @Override
    public Set<String> listResources(String prefix) {
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        Set<String> result = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(normalizedPrefix)) {
                result.add(entry.getName());
            }
        }
        return result;
    }

    @Override
    public PackMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private PackMetadata parseMetadata() {
        ZipEntry mcmeta = zipFile.getEntry("pack.mcmeta");
        if (mcmeta == null) {
            throw new IllegalArgumentException("pack.mcmeta not found in zip");
        }
        try (InputStream is = zipFile.getInputStream(mcmeta)) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return PackMetadata.fromJson(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read pack.mcmeta from zip", e);
        }
    }

    /**
     * Fail-fast zip-slip scan. Refuses any entry whose normalised name contains a
     * {@code..} segment, starts with a separator, or is an absolute path.
     *
     * <p>Runs once at construction so the pack can never be handed a malicious archive.
     */
    private static void scanForUnsafeEntries(ZipFile zf) {
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            assertSafeZipEntryName(entry.getName());
        }
    }

    /**
     * Validates an entry name as stored inside the zip. Both forward slashes (POSIX) and
     * backslashes (Windows) are checked so a malicious producer cannot smuggle a traversal
     * via the alternate separator.
     */
    private static void assertSafeZipEntryName(String name) {
        if (name == null) {
            throw new ResourcePackException("Zip-slip rejected: null entry name");
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            throw new ResourcePackException(
                    "Zip-slip rejected on load: absolute path entry '" + name + "'");
        }
        // Windows drive-letter prefix e.g. "C:"
        if (name.length() >= 2 && name.charAt(1) == ':') {
            throw new ResourcePackException(
                    "Zip-slip rejected on load: drive-letter entry '" + name + "'");
        }
        // Reject any segment that is exactly "..".
        for (String segment : name.split("[/\\\\]")) {
            if ("..".equals(segment)) {
                throw new ResourcePackException(
                        "Zip-slip rejected on load: '..' segment in entry '" + name + "'");
            }
        }
    }

    /**
     * Validates a caller-supplied relative lookup path before forwarding to the zip. Rejects
     * absolute paths and any {@code..} segment even if normalise would collapse it.
     */
    private static void assertSafeRelativePath(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new ResourcePackException(
                    "Path-traversal rejected: absolute path '" + path + "'");
        }
        if (path.length() >= 2 && path.charAt(1) == ':') {
            throw new ResourcePackException(
                    "Path-traversal rejected: drive-letter path '" + path + "'");
        }
        for (String segment : path.split("[/\\\\]")) {
            if ("..".equals(segment)) {
                throw new ResourcePackException(
                        "Path-traversal rejected: '..' segment in '" + path + "'");
            }
        }
    }

    private static void closeQuietly(ZipFile zf, Throwable primary) {
        try {
            zf.close();
        } catch (IOException closeEx) {
            primary.addSuppressed(closeEx);
        }
    }
}
