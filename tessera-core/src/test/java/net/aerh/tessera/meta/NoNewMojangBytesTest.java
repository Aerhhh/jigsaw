package net.aerh.tessera.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts: the only file type under a per-version
 * {@code tessera/assets/<version>/} resource tree is {@code manifest.json}. Any binary
 * (PNG, OGG, OTF, BIN, etc.) under that tree would indicate Mojang-owned bytes were committed.
 *
 * <p>Earlier revisions created the tree under {@code tessera-core}; a later module split
 * moved the tessera/assets tree into the per-version asset artifact
 * {@code tessera-assets-26.1.2}. The assertion now points at the post-split location.
 * Does NOT police the pre-existing {@code tessera-core/src/main/resources/minecraft/assets/}
 * tree; that tree is stripped elsewhere and its residue does not fall under this gate.
 */
class NoNewMojangBytesTest {

    @Test
    void onlyManifestJsonUnderTesseraAssetsResources() throws IOException {
        Path root = repoRoot().resolve("tessera-assets-26.1.2/src/main/resources/tessera/assets");
        if (!Files.isDirectory(root)) {
            throw new AssertionError(
                    "Expected directory " + root + " to exist; asset artifact module missing?");
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> nonJson = walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                    .toList();
            assertThat(nonJson)
                    .as("tessera-assets modules must not add any file other than manifest.json under tessera/assets/*/")
                    .isEmpty();
        }
    }

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isRegularFile(p.resolve("jitpack.yml"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }
}
