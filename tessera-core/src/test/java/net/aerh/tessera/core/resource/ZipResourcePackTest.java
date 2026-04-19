package net.aerh.tessera.core.resource;

import net.aerh.tessera.api.exception.ResourcePackException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipResourcePackTest {

    @TempDir
    Path tempDir;

    private Path createMinimalZipPack() throws IOException {
        Path zipPath = tempDir.resolve("test_pack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("""
                    {
                      "pack": {
                        "pack_format": 34,
                        "description": "Zip test pack"
                      }
                    }
                    """.getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("assets/minecraft/textures/item/test.png"));
            zos.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("assets/minecraft/models/item/sword.json"));
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        return zipPath;
    }

    @Test
    void metadata_parsesPackMcmeta() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThat(pack.metadata().packFormat()).isEqualTo(34);
            assertThat(pack.metadata().description()).isEqualTo("Zip test pack");
        }
    }

    @Test
    void constructor_missingPackMcmeta_throws() throws IOException {
        Path zipPath = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("dummy.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }
        assertThatThrownBy(() -> new ZipResourcePack(zipPath))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getResource_existingEntry_returnsStream() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            Optional<InputStream> stream = pack.getResource("assets/minecraft/textures/item/test.png");
            assertThat(stream).isPresent();
            stream.get().close();
        }
    }

    @Test
    void getResource_missingEntry_returnsEmpty() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThat(pack.getResource("nonexistent.txt")).isEmpty();
        }
    }

    @Test
    void hasResource_existingEntry_returnsTrue() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThat(pack.hasResource("pack.mcmeta")).isTrue();
            assertThat(pack.hasResource("missing.txt")).isFalse();
        }
    }

    @Test
    void listResources_returnsMatchingPaths() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            Set<String> resources = pack.listResources("assets/minecraft/models/item/");
            assertThat(resources).containsExactly("assets/minecraft/models/item/sword.json");
        }
    }

    // -------------------------------------------------------------------------
    // Zip-slip + path-traversal hardening
    // -------------------------------------------------------------------------

    /**
     * Builds a zip with the given entry name planted alongside a valid pack.mcmeta.
     */
    private Path createZipWithEntry(String entryName) throws IOException {
        Path zipPath = tempDir.resolve("malicious_" + System.nanoTime() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("""
                    {"pack":{"pack_format":34,"description":"fixture"}}
                    """.getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(new byte[]{0});
            zos.closeEntry();
        }
        return zipPath;
    }

    @Test
    void construction_refuses_zipslip_entry_with_dotdot() throws IOException {
        Path zip = createZipWithEntry("../../../etc/passwd");
        assertThatThrownBy(() -> new ZipResourcePack(zip))
                .isInstanceOf(ResourcePackException.class)
                .hasMessageContaining("..");
    }

    @Test
    void construction_refuses_absolute_path_entry() throws IOException {
        Path zip = createZipWithEntry("/etc/passwd");
        assertThatThrownBy(() -> new ZipResourcePack(zip))
                .isInstanceOf(ResourcePackException.class);
    }

    @Test
    void getResource_dotdot_in_requested_path_throws() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThatThrownBy(() -> pack.getResource("assets/../../../etc/passwd"))
                    .isInstanceOf(ResourcePackException.class)
                    .hasMessageContaining("..");
        }
    }

    @Test
    void getResource_absolute_request_path_throws() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThatThrownBy(() -> pack.getResource("/etc/passwd"))
                    .isInstanceOf(ResourcePackException.class);
        }
    }

    @Test
    void getResource_null_path_throws_npe() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThatThrownBy(() -> pack.getResource(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void close_idempotent() throws IOException {
        Path zipPath = createMinimalZipPack();
        ZipResourcePack pack = new ZipResourcePack(zipPath);
        pack.close();
        // Second close() must not throw; java.util.zip.ZipFile is idempotent on close.
        pack.close();
    }
}
