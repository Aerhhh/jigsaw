package net.aerh.tessera.api.exception;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thrown by {@code Engine.builder().build()} when no registered
 * {@link net.aerh.tessera.api.assets.AssetProvider} declares support for the requested
 * {@code minecraftVersion(String)}.
 *
 * <p>This exception is unchecked and extends {@link TesseraException}, matching the
 * {@link ValidationException} / {@link RegistryException} / {@link EffectException} /
 * {@link UnknownItemException} precedent for builder-time errors. The
 * {@code Engine.builder().build()} {@code throws} clause stays at
 * {@code throws TesseraAssetsMissingException} (the existing checked one); this new
 * exception is invisible in signatures.
 *
 * <p>The message contains: requested version, all currently-registered provider versions,
 * a JitPack coordinate example, and a programmatic-registration code snippet.
 */
public final class UnsupportedMinecraftVersionException extends TesseraException {

    /**
     * Constructs a new {@code UnsupportedMinecraftVersionException}.
     *
     * @param requested the unmatched MC version the consumer asked for
     * @param registered all versions currently registered across auto-discovered + programmatic providers
     * @param tesseraVersion the running Tessera version (used in the JitPack snippet)
     */
    public UnsupportedMinecraftVersionException(String requested,
                                                Collection<String> registered,
                                                String tesseraVersion) {
        super(buildMessage(requested, registered, tesseraVersion), Map.of(
                "requested", requested,
                "registered", registered.toString(),
                "tesseraVersion", tesseraVersion));
    }

    private static String buildMessage(String requested, Collection<String> registered,
                                       String tesseraVersion) {
        return """
                Tessera has no AssetProvider for Minecraft version "%s".

                Currently registered providers:
                %s

                To add support for %s, depend on the matching asset artifact:

                  <dependency>
                    <groupId>com.github.Aerhhh.tessera</groupId>
                    <artifactId>tessera-assets-%s</artifactId>
                    <version>%s</version>
                  </dependency>

                Or register a provider programmatically:

                  Engine.builder()
                      .minecraftVersion("%s")
                      .assetProvider(new MyCustomAssetProvider())
                      .build();
                """.formatted(
                requested,
                registered.isEmpty()
                        ? "  (none - have you declared a tessera-assets-<mcver> dependency?)"
                        : registered.stream().map(v -> "  - " + v).collect(Collectors.joining("\n")),
                requested, requested, tesseraVersion, requested);
    }
}
