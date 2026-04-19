package net.aerh.tessera.api.assets;

import net.aerh.tessera.api.exception.TesseraEulaNotAcceptedException;

/**
 *  EULA gate. The Mojang EULA must have been accepted via one of three paths:
 * builder flag, environment variable, or JVM system property.
 */
public final class EulaGate {

    private EulaGate() { /* static-only */ }

    /**
     * Verifies the Mojang EULA has been accepted. Returns silently if so; throws otherwise.
     *
     * @param builderFlag the value passed to
     *                    {@link net.aerh.tessera.api.EngineBuilder#acceptMojangEula(boolean)}
     * @throws TesseraEulaNotAcceptedException if none of the three acceptance paths are set
     */
    public static void requireEulaAcceptance(boolean builderFlag) {
        if (builderFlag) return;
        if ("true".equalsIgnoreCase(System.getenv("TESSERA_ACCEPT_MOJANG_EULA"))) return;
        if ("true".equalsIgnoreCase(System.getProperty("tessera.accept.mojang.eula"))) return;
        throw new TesseraEulaNotAcceptedException(
                "Tessera downloads official Minecraft assets from Mojang's CDN at first use.\n"
              + "You must accept the Mojang EULA (https://www.minecraft.net/en-us/eula) via one of:\n"
              + "  - EngineBuilder.acceptMojangEula(true), OR\n"
              + "  - environment variable TESSERA_ACCEPT_MOJANG_EULA=true, OR\n"
              + "  - system property -Dtessera.accept.mojang.eula=true"
        );
    }
}
