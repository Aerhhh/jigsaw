package net.aerh.tessera.api.assets;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One entry in an asset manifest.
 *
 * @param path the virtual asset path (e.g. {@code "minecraft/textures/block/diamond_ore.png"});
 *             must not contain {@code ".."} segments, backslashes, or a leading slash
 * @param sha1 the expected SHA1 hex digest of the file; lowercase, exactly 40 characters
 * @param size the expected file size in bytes; must be non-negative
 * @param url the absolute URL to fetch the file from (typically {@code resources.download.minecraft.net})
 */
public record AssetEntry(String path, String sha1, long size, String url) {

    private static final Pattern SHA1_PATTERN = Pattern.compile("^[0-9a-f]{40}$");

    /**
     * Compact constructor that enforces every invariant declared on the record components.
     * Null inputs raise {@link NullPointerException}; malformed values raise
     * {@link IllegalArgumentException}.
     */
    public AssetEntry {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(sha1, "sha1 must not be null");
        Objects.requireNonNull(url, "url must not be null");
        if (!SHA1_PATTERN.matcher(sha1).matches()) {
            throw new IllegalArgumentException("sha1 must be 40 lowercase hex chars, got: " + sha1);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0, got: " + size);
        }
        if (path.contains("..") || path.contains("\\") || path.startsWith("/")) {
            throw new IllegalArgumentException("path contains forbidden characters (.. / \\ or leading /): " + path);
        }
    }
}
