package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.exception.UnsupportedMinecraftVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Resolves the {@link AssetProvider} for a given Minecraft version:
 * <ul>
 *   <li>Programmatic registrations (from {@code Builder.assetProvider(...)}) win over
 *       {@link ServiceLoader}-discovered providers on version collision.</li>
 *   <li>Duplicate programmatic registration of the same version: last-wins + WARN log
 *       naming both classes.</li>
 *   <li>Strict exact-string version matching (no wildcards, no normalization).</li>
 *   <li>Unmatched version: throws {@link UnsupportedMinecraftVersionException} with
 *       a rich message (requested version, registered list, JitPack snippet,
 *       programmatic-registration snippet, doc link).</li>
 * </ul>
 *
 * <p>Package-internal helper for {@code DefaultEngine.Builder}; not part of the public api.
 */
public final class AssetProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(AssetProviderResolver.class);

    private AssetProviderResolver() { /* static-only */ }

    /**
     * Resolves the {@link AssetProvider} for {@code mcVersion}.
     *
     * @param mcVersion the requested Minecraft version (exact-string match)
     * @param builderRegistered providers registered programmatically via
     *                          {@code Builder.assetProvider(...)}; iteration order is honored
     *                          (last-wins on duplicate-version collision)
     * @param tesseraVersion the running Tessera version, embedded in the
     *                          {@link UnsupportedMinecraftVersionException} message on miss
     * @return the resolved provider
     * @throws UnsupportedMinecraftVersionException if no registered provider declares support for
     *                                              {@code mcVersion}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static AssetProvider resolve(String mcVersion,
                                        List<AssetProvider> builderRegistered,
                                        String tesseraVersion) {
        Objects.requireNonNull(mcVersion, "mcVersion must not be null");
        Objects.requireNonNull(builderRegistered, "builderRegistered must not be null");
        Objects.requireNonNull(tesseraVersion, "tesseraVersion must not be null");

        // Detect duplicate programmatic registrations: warn on collision; last-wins.
        List<AssetProvider> deduped = dedupeProgrammatic(builderRegistered);

        // Programmatic providers are tried first (they win on collision).
        // ServiceLoader-discovered providers fill in any versions the programmatic set doesn't cover.
        List<AssetProvider> all = new ArrayList<>(deduped);
        for (AssetProvider auto : ServiceLoader.load(AssetProvider.class)) {
            boolean collision = all.stream().anyMatch(existing ->
                    !Collections.disjoint(existing.supportedVersions(), auto.supportedVersions()));
            if (!collision) {
                all.add(auto);
            }
        }

        return all.stream()
                .filter(p -> p.supportedVersions().contains(mcVersion))
                .findFirst()
                .orElseThrow(() -> new UnsupportedMinecraftVersionException(
                        mcVersion,
                        all.stream()
                                .flatMap(p -> p.supportedVersions().stream())
                                .collect(Collectors.toCollection(TreeSet::new)),
                        tesseraVersion));
    }

    /**
     * Dedup step: if two programmatic registrations declare the same version, the LATER
     * registration replaces the earlier and a WARN log is emitted naming both classes.
     */
    private static List<AssetProvider> dedupeProgrammatic(List<AssetProvider> programmatic) {
        Map<String, AssetProvider> latest = new LinkedHashMap<>();
        for (AssetProvider p : programmatic) {
            for (String v : p.supportedVersions()) {
                AssetProvider prior = latest.put(v, p);
                if (prior != null && prior != p) {
                    log.warn("Duplicate AssetProvider registration for version '{}': {} replaces {}",
                            v, p.getClass().getName(), prior.getClass().getName());
                }
            }
        }
        // Preserve registration order for the surviving providers.
        return new ArrayList<>(new LinkedHashSet<>(latest.values()));
    }
}
