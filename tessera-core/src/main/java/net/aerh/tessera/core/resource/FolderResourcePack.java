package net.aerh.tessera.core.resource;

import net.aerh.tessera.api.exception.ResourcePackException;
import net.aerh.tessera.api.resource.PackMetadata;
import net.aerh.tessera.api.resource.ResourcePack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link ResourcePack} backed by a directory on the filesystem.
 *
 * <p>Path-traversal hardening: {@link #getResource} rejects any caller-supplied path that
 * escapes the pack root after {@link Path#normalize()}. Absolute paths, {@code..} segments,
 * and paths that resolve outside the normalised root all throw {@link ResourcePackException}.
 */
public class FolderResourcePack implements ResourcePack {

    private final Path root;
    private final Path rootAbsNormalized;
    private final PackMetadata metadata;

    public FolderResourcePack(Path root) {
        Objects.requireNonNull(root, "root must not be null");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }
        this.root = root;
        this.rootAbsNormalized = root.toAbsolutePath().normalize();
        this.metadata = parseMetadata(root.resolve("pack.mcmeta"));
    }

    @Override
    public Optional<InputStream> getResource(String path) {
        Objects.requireNonNull(path, "path must not be null");
        Path resolved = resolveOrReject(path);
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(resolved));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves {@code path} against the pack root and asserts the result is contained
     * inside the absolute-normalised root. Rejects absolute inputs up-front so a caller
     * cannot bypass the startsWith check via an absolute request.
     */
    private Path resolveOrReject(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new ResourcePackException(
                    "Path-traversal rejected: absolute path '" + path + "'");
        }
        if (path.length() >= 2 && path.charAt(1) == ':') {
            throw new ResourcePackException(
                    "Path-traversal rejected: drive-letter path '" + path + "'");
        }
        Path candidate;
        try {
            candidate = root.resolve(path).toAbsolutePath().normalize();
        } catch (java.nio.file.InvalidPathException e) {
            throw new ResourcePackException(
                    "Path-traversal rejected: invalid path '" + path + "'", e);
        }
        if (!candidate.startsWith(rootAbsNormalized)) {
            throw new ResourcePackException(
                    "Path-traversal rejected: resolved path '" + candidate
                            + "' escapes pack root '" + rootAbsNormalized + "'");
        }
        return candidate;
    }

    @Override
    public boolean hasResource(String path) {
        Objects.requireNonNull(path, "path must not be null");
        // Route through the same traversal guard as getResource. Adversarial lookups get
        // an innocuous `false` rather than a stack trace so probes are not amplified into
        // a signal, but the pack root is still enforced.
        Path resolved;
        try {
            resolved = resolveOrReject(path);
        } catch (ResourcePackException e) {
            return false;
        }
        return Files.isRegularFile(resolved);
    }

    @Override
    public Set<String> listResources(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        // Guard the prefix the same way as getResource. A rejected prefix returns an
        // empty set (consistent with how a missing prefix dir already behaves here).
        Path prefixPath;
        try {
            prefixPath = resolveOrReject(prefix);
        } catch (ResourcePackException e) {
            return Set.of();
        }
        if (!Files.isDirectory(prefixPath)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(prefixPath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }

    @Override
    public PackMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() {
        // No resources to close for folder packs
    }

    private static PackMetadata parseMetadata(Path mcmetaPath) {
        if (!Files.isRegularFile(mcmetaPath)) {
            throw new IllegalArgumentException("pack.mcmeta not found: " + mcmetaPath);
        }
        try {
            String json = Files.readString(mcmetaPath);
            return PackMetadata.fromJson(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read pack.mcmeta: " + mcmetaPath, e);
        }
    }
}
