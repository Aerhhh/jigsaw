package net.aerh.tessera.api.assets;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Optional {@code client_jar} section of an {@link AssetManifest}. Carries the URL + SHA-1 of
 * Mojang's {@code client.jar} so downstream extractors can fetch the item/block texture roots
 * that piston-meta's top-level {@code objects} list does NOT include (Mojang bundles those
 * inside {@code client.jar} post-1.19).
 *
 * <p>: source of truth is piston-meta's {@code downloads.client} object for a
 * given Minecraft version.
 *
 * @param url the absolute HTTPS URL to fetch {@code client.jar} from
 * @param sha1 the 40-char lowercase SHA-1 digest; verified against the downloaded bytes
 * @param size the declared file size in bytes; must be non-negative
 */
public record ClientJarSection(String url, String sha1, long size) {

    private static final Pattern SHA1_PATTERN = Pattern.compile("^[0-9a-f]{40}$");

    /**
     * Compact constructor. Null inputs raise {@link NullPointerException}; malformed values
     * raise {@link IllegalArgumentException}.
     */
    public ClientJarSection {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(sha1, "sha1 must not be null");
        if (!SHA1_PATTERN.matcher(sha1).matches()) {
            throw new IllegalArgumentException("sha1 must be 40 lowercase hex chars, got: " + sha1);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0, got: " + size);
        }
    }
}
