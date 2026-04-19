package net.aerh.tessera.api.assets;

import java.util.Objects;

/**
 * Advisory capability flags describing the currently-resolved {@link AssetProvider}.
 *
 * <p>Per decision, capabilities DO NOT participate in resolution — they are a
 * read-only introspection surface. Populated at {@code Engine.builder().build()} time
 * and constant for the engine's lifetime.
 *
 * @param hasComponents true if the MC version supports data components (1.20.5+)
 * @param hasFlattening true if the MC version is post-flattening (1.13+)
 * @param supportsAnimatedInventory true if inventory animations are expected
 * @param mcVersion the resolved Minecraft version string; must not be {@code null}
 */
public record Capabilities(
        boolean hasComponents,
        boolean hasFlattening,
        boolean supportsAnimatedInventory,
        String mcVersion) {

    public Capabilities {
        Objects.requireNonNull(mcVersion, "mcVersion must not be null");
    }
}
