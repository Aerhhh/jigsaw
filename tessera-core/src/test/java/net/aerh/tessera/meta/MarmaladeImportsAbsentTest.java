package net.aerh.tessera.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that no Java source file under {@code tessera-core/src/main} or
 * {@code tessera-core/src/test} references the Marmalade package
 * {@code net.hypixel.nerdbot.marmalade} after the dependency was swapped out.
 *
 * <p>The forbidden token is assembled at runtime so a blanket find-and-replace cannot
 * accidentally mutate it (mirrors the pattern in
 * {@link NoLegacyPackagesTest}).
 */
class MarmaladeImportsAbsentTest {

    private static final String FORBIDDEN_TOKEN = "net.hypixel.nerdbot." + "marmalade";

    @Test
    void noJavaSourceReferencesMarmaladePackage() throws IOException {
        Path repoRoot = locateRepoRoot();
        List<Path> sourceRoots = List.of(
                repoRoot.resolve("tessera-core/src/main/java"),
                repoRoot.resolve("tessera-core/src/test/java"),
                repoRoot.resolve("skyblock/src/main/java"),
                repoRoot.resolve("skyblock/src/test/java")
        );

        for (Path root : sourceRoots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(MarmaladeImportsAbsentTest::isNotThisTestFile)
                    .forEach(MarmaladeImportsAbsentTest::assertFileHasNoMarmaladeReference);
            }
        }
    }

    /**
     * Excludes this test file itself; its Javadoc necessarily mentions the forbidden token
     * as the thing being forbidden. No other file has any legitimate reason to contain it.
     */
    private static boolean isNotThisTestFile(Path file) {
        return !file.getFileName().toString().equals("MarmaladeImportsAbsentTest.java");
    }

    private static void assertFileHasNoMarmaladeReference(Path file) {
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(body)
                    .as("file %s must not reference Marmalade (token '%s')", file, FORBIDDEN_TOKEN)
                    .doesNotContain(FORBIDDEN_TOKEN);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }

    private static Path locateRepoRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path p = cwd;
        while (p != null) {
            if (Files.isRegularFile(p.resolve("pom.xml"))
                    && Files.isRegularFile(p.resolve("jitpack.yml"))) {
                return p;
            }
            p = p.getParent();
        }
        return cwd;
    }
}
