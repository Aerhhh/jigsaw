package net.aerh.tessera.testkit.cli;

import net.aerh.tessera.api.assets.TesseraAssets;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tiny {@code main()} launcher invoked by {@code.github/workflows/capture-baselines.yml}
 * to hydrate {@code ~/.tessera/assets/<mcVer>/} on a fresh GitHub-hosted runner before
 * {@link net.aerh.tessera.testkit.golden.GoldenFixturesTest} runs with
 * {@code -Dtessera.golden.update=true}.
 *
 * <p>Why not the plan's proposed {@code /tmp/Hydrate.java} + plain {@code exec:java} on
 * {@code tessera-assets-26.1.2}? Two reasons:
 * <ol>
 *   <li>{@code TesseraAssets} lives in {@code net.aerh.tessera.api.assets}, not
 *       {@code net.aerh.tessera.assets} - the plan's import was incorrect. Writing a
 *       /tmp file keeps the mistake invisible to the compiler and would silently fail
 *       at workflow runtime.</li>
 *   <li>{@link net.aerh.tessera.api.Engine.Builder#build()} does NOT call
 *       {@link TesseraAssets#fetch} on cache-miss. It verifies presence and throws
 *       {@link net.aerh.tessera.api.exception.TesseraAssetsMissingException} if the
 *       manifest files are missing. So the plan's fallback "let BeforeAll
 *       engine.build() hydrate on-demand" does not actually hydrate. An explicit
 *       fetch call is required.</li>
 * </ol>
 *
 * <p>Test-scope class rather than main-scope because the capture-baselines workflow
 * is a test-oriented automation, not a consumer API. Keeping it under
 * {@code src/test/java} means the testkit-published jar does not carry this CLI class
 * into consumer dependency graphs.
 *
 * <p>Usage (workflow invokes this via {@code exec:java}):
 * <pre>{@code
 * ./mvnw -B -ntp -pl tessera-testkit -am test-compile
 * ./mvnw -B -ntp -pl tessera-testkit \
 *     org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *     -Dexec.mainClass=net.aerh.tessera.testkit.cli.CaptureBaselinesHydrator \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="26.1.2"
 * }</pre>
 *
 * <p>Honours the same EULA gates as {@link TesseraAssets#fetch} itself: requires either
 * {@code TESSERA_ACCEPT_MOJANG_EULA=true} env or
 * {@code -Dtessera.accept.mojang.eula=true} system property, or an explicit
 * {@code --accept-eula} argument (which this launcher translates into the builder-level
 * flag overload).
 */
public final class CaptureBaselinesHydrator {

    private CaptureBaselinesHydrator() {
        /* static-main only */
    }

    /**
     * Hydrates the asset cache for the requested Minecraft version.
     *
     * @param args {@code [mcVer, (optional) --accept-eula]}. {@code mcVer} is required.
     * @throws Exception if asset download or SHA-1 integrity verification fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: CaptureBaselinesHydrator <mcVer> [--accept-eula]");
            System.err.println("  EULA acceptance also honoured via TESSERA_ACCEPT_MOJANG_EULA=true env");
            System.err.println("  or -Dtessera.accept.mojang.eula=true system property.");
            System.exit(1);
            return;
        }
        String mcVer = args[0];
        boolean builderAcceptedEula = args.length > 1 && "--accept-eula".equals(args[1]);

        // Null cacheDirOverride = default ~/.tessera/assets/<mcVer>/ resolution per
        // tessera-api's CacheLocator. No need to override on the workflow runner.
        Path cacheDirOverride = null;

        System.out.println("CaptureBaselinesHydrator: fetching assets for MC " + mcVer
                + " (builderAcceptedEula=" + builderAcceptedEula + ")");
        TesseraAssets.fetch(mcVer, cacheDirOverride, builderAcceptedEula);
        // Log the resolved cache dir so the workflow step's stdout makes it obvious
        // where assets landed, useful if a contributor inspects the runner log.
        System.out.println("CaptureBaselinesHydrator: complete; cache root ~ "
                + Paths.get(System.getProperty("user.home"), ".tessera", "assets", mcVer));
    }
}
