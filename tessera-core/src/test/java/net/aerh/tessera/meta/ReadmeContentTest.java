package net.aerh.tessera.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts {@code README.md} reflects Tessera positioning plus the Mojang EULA
 * notice. Covers the full set of positioning, coordinate, quickstart-bootstrap,
 * and EULA-acceptance-path assertions.
 */
class ReadmeContentTest {

    @Test
    void readmeReflectsTesseraPositioning() throws IOException {
        Path readme = locateRepoRoot().resolve("README.md");
        String body = Files.readString(readme, StandardCharsets.UTF_8);

        // --- Identity / positioning ---
        assertThat(body).contains("Tessera");
        assertThat(body).contains("pixel-perfect");
        assertThat(body).contains("com.github.Aerhhh.Tessera");

        // --- Quickstart bootstrap ---
        assertThat(body).contains("TesseraAssets.fetch");
        assertThat(body).contains("Engine.builder()");

        // --- EULA notice ---
        assertThat(body).contains("minecraft.net/en-us/eula");
        assertThat(body).contains("acceptMojangEula");
        assertThat(body).contains("TESSERA_ACCEPT_MOJANG_EULA");
        assertThat(body).contains("tessera.accept.mojang.eula");

        // --- No lingering Jigsaw artifact coordinate. Legitimate mentions of the prior NAME
        //     in a migration-guide context are fine; this guard catches the artifact
        //     coordinate specifically, which must never appear in the Tessera README.
        assertThat(body).doesNotContain("<artifactId>jigsaw</artifactId>");
        assertThat(body).doesNotContain("com.github.Aerhhh.Jigsaw:jigsaw");
    }

    private static Path locateRepoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isRegularFile(p.resolve("jitpack.yml"))) return p;
            p = p.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }
}
