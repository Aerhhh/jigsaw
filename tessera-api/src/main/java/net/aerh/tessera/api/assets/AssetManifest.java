package net.aerh.tessera.api.assets;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A pinned list of asset files for a single Minecraft version, plus an optional
 * {@link ClientJarSection client.jar} descriptor for extractor-driven asset roots
 * (textures/models/font/etc that piston-meta does not list).
 *
 * @param version the Minecraft version string (e.g. {@code "1.21.4"})
 * @param files the asset entries; defensively copied to an unmodifiable list
 * @param clientJar optional {@link ClientJarSection} for post-1.19 textures/models; may be
 *                  {@code null} on older manifests that predate the client.jar extension
 */
public record AssetManifest(String version, List<AssetEntry> files,
                            @SerializedName("client_jar") ClientJarSection clientJar) {

    /**
     * Compact constructor that defensively copies {@code files}. Null {@code clientJar} is
     * permitted for backward compatibility with older manifests that carry no client.jar section.
     */
    public AssetManifest {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(files, "files must not be null");
        files = List.copyOf(files);
    }

    /**
     * Backward-compatibility constructor for manifests with no {@code clientJar} section.
     *
     * @param version the Minecraft version string
     * @param files the asset entries
     */
    public AssetManifest(String version, List<AssetEntry> files) {
        this(version, files, null);
    }

    /**
     * Returns the optional {@link ClientJarSection} as an {@link Optional} for ergonomic null
     * handling at the consumer.
     *
     * @return an {@link Optional} containing the client jar section, or empty if absent
     */
    public Optional<ClientJarSection> optionalClientJar() {
        return Optional.ofNullable(clientJar);
    }
}
