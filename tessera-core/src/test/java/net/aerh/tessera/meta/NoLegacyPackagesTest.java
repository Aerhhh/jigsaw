package net.aerh.tessera.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against accidental reintroduction of legacy {@code net.aerh.jigsaw.*}
 * package paths post-Phase-1 rename, and verifies that every reactor module has its
 * source tree in the expected location post-Phase-2 split.
 *
 * <p>The Phase-1 version of this test hardcoded {@code tessera-core/src/main/java} + 3
 * peers and would silently pass after the Phase-2 multi-module split, because most
 * source files moved out of tessera-core to tessera-api / tessera-spi /
 * tessera-assets-26.1.2 / tessera-testkit / tessera-http. The current rewrite walks the
 * repo dynamically and asserts at least 7 reactor-module source roots are found (the
 * 8-module reactor provides up to 16 source roots; {@code tessera-bom} has no Java
 * sources), preventing future silent-pass regressions.
 *
 * <p>Legacy tokens are assembled at runtime so a blanket {@code sed} rewrite cannot
 * accidentally mutate the test literals (the Phase-1 defense; preserved here).
 */
class NoLegacyPackagesTest {

    /** Minimum number of source roots the 8-module reactor must expose. */
    private static final int MIN_SOURCE_ROOTS = 7;

    private static final String LEGACY_PKG = "net.aerh." + "jigsaw";
    private static final String LEGACY_EXCEPTION = "J" + "igsawException";

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "package " + LEGACY_PKG,
            "import " + LEGACY_PKG,
            LEGACY_EXCEPTION
    );

    @Test
    void reactor_has_at_least_seven_source_roots_and_no_legacy_packages() throws IOException {
        Path repoRoot = findRepoRoot();
        List<Path> sourceRoots = findSourceRoots(repoRoot);

        // Sanity: the 8-module reactor (tessera-api/spi/bom/core/assets-26.1.2/testkit/http + skyblock)
        // provides up to ~16 source roots (main + test per non-bom module). Assert >=7 to catch any
        // future silent-regression if a module's source tree is accidentally deleted or the
        // reactor loses modules.
        assertThat(sourceRoots)
                .as("expected at least %d reactor module source roots under %s; found: %s",
                        MIN_SOURCE_ROOTS, repoRoot, sourceRoots)
                .hasSizeGreaterThanOrEqualTo(MIN_SOURCE_ROOTS);

        List<String> violations = new ArrayList<>();
        for (Path root : sourceRoots) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.getFileName() != null
                                && p.getFileName().toString().endsWith(".java")
                                && Files.isRegularFile(p))
                        .forEach(p -> collectLegacyTokenViolations(p, violations));
            }
        }
        assertThat(violations)
                .as("no file should contain banned legacy package references")
                .isEmpty();
    }

    private static List<Path> findSourceRoots(Path repoRoot) throws IOException {
        List<Path> sourceRoots = new ArrayList<>();
        Path mainJava = Paths.get("src", "main", "java");
        Path testJava = Paths.get("src", "test", "java");
        try (Stream<Path> walk = Files.walk(repoRoot, 4)) {
            walk.filter(Files::isDirectory)
                    .filter(p -> p.endsWith(mainJava) || p.endsWith(testJava))
                    // Exclude.planning/ scratch trees and any target/ intermediate output.
                    .filter(p -> !p.toString().contains(".planning"))
                    .filter(p -> !p.toString().contains("target"))
                    .forEach(sourceRoots::add);
        }
        return sourceRoots;
    }

    private static void collectLegacyTokenViolations(Path file, List<String> violations) {
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN_TOKENS) {
                if (body.contains(forbidden)) {
                    violations.add(file + " -> " + forbidden);
                }
            }
        } catch (IOException e) {
            // Skip unreadable files; the walk is best-effort for a repo-hygiene gate.
        }
    }

    private static Path findRepoRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path p = cwd;
        while (p != null) {
            Path pom = p.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                try {
                    String content = Files.readString(pom);
                    // The aggregator pom is identified by BOTH carrying the
                    // tessera-parent artifactId AND declaring <modules>. Child poms
                    // (tessera-core etc.) reference tessera-parent inside <parent>
                    // but have no <modules> element and a different artifactId.
                    if (content.contains("<artifactId>tessera-parent</artifactId>")
                            && content.contains("<modules>")) {
                        return p;
                    }
                } catch (IOException ignored) {
                    // fall through
                }
            }
            p = p.getParent();
        }
        throw new IllegalStateException(
                "Could not locate tessera-parent pom.xml from " + cwd);
    }
}
