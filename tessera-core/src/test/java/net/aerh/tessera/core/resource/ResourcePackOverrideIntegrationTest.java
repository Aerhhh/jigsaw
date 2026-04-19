package net.aerh.tessera.core.resource;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.exception.ResourcePackException;
import net.aerh.tessera.api.resource.ResourcePack;
import net.aerh.tessera.core.generator.DefaultEngineBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end verification that {@code Engine.builder().resourcePack(...)} threads the
 * user-supplied pack into {@link DefaultEngineBuilder#build()} and the engine closes
 * it on shutdown.
 *
 * <p>Five behaviour cases track the contract:
 * <ol>
 *   <li>override pack loaded (FolderResourcePack accepted).</li>
 *   <li>zip pack loaded (ZipResourcePack accepted).</li>
 *   <li>null pack: resourcePack(null) throws NPE (builder rejects at call-site).</li>
 *   <li>zip-slip entry rejected at ZipResourcePack construction.</li>
 *   <li>folder path-traversal rejected at FolderResourcePack.getResource call.</li>
 * </ol>
 *
 * <p>Engine-construction cases that require a hydrated 26.1.2 asset cache are
 * env-gated on {@code TESSERA_ASSETS_AVAILABLE=true}; the zip-slip / traversal
 * guards run unconditionally because they don't require an engine build.
 */
final class ResourcePackOverrideIntegrationTest {

    // -----------------------------------------------------------------------
    // Unit-level guard tests (no engine, no asset cache)
    // -----------------------------------------------------------------------

    @Test
    void resourcePack_null_rejected_at_builder() {
        DefaultEngineBuilder builder = (DefaultEngineBuilder) Engine.builder();
        assertThatThrownBy(() -> builder.resourcePack(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zip_slip_entry_rejected_at_construction(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("malicious.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("""
                    {"pack":{"pack_format":34,"description":"evil"}}
                    """.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("../../../etc/passwd"));
            zos.write("smuggled".getBytes());
            zos.closeEntry();
        }

        assertThatThrownBy(() -> new ZipResourcePack(zip))
                .isInstanceOf(ResourcePackException.class)
                .hasMessageContaining("..");
    }

    @Test
    void folder_path_traversal_rejected_at_getResource(@TempDir Path tempDir) throws IOException {
        Path packDir = tempDir.resolve("pack");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":34,\"description\":\"ok\"}}");

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            assertThatThrownBy(() -> pack.getResource("../../etc/passwd"))
                    .isInstanceOf(ResourcePackException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Engine-level cases (require hydrated 26.1.2 asset cache)
    // -----------------------------------------------------------------------

    @Test
    @EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
    void override_folder_pack_threads_into_engine(@TempDir Path tempDir) throws Exception {
        Path packDir = tempDir.resolve("custom");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":34,\"description\":\"custom\"}}");

        ClosingRecordingPackWrapper recorder;
        try (FolderResourcePack inner = new FolderResourcePack(packDir)) {
            recorder = new ClosingRecordingPackWrapper(inner);
            try (Engine engine = Engine.builder()
                    .minecraftVersion("26.1.2")
                    .acceptMojangEula(true)
                    .resourcePack(recorder)
                    .build()) {
                assertThat(engine).isNotNull();
            }
        }

        // Engine.close() must have closed our wrapper (which wraps the inner pack in a
        // LayeredResourcePack inside the engine). Close count >= 1.
        assertThat(recorder.closeCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
    void override_zip_pack_threads_into_engine(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("custom.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("""
                    {"pack":{"pack_format":34,"description":"zip"}}
                    """.getBytes());
            zos.closeEntry();
        }

        try (ZipResourcePack pack = new ZipResourcePack(zip);
             Engine engine = Engine.builder()
                     .minecraftVersion("26.1.2")
                     .acceptMojangEula(true)
                     .resourcePack(pack)
                     .build()) {
            assertThat(engine).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------------

    /** Wraps a ResourcePack and records close() invocations for engine-shutdown assertions. */
    private static final class ClosingRecordingPackWrapper implements ResourcePack {
        private final ResourcePack inner;
        private final java.util.concurrent.atomic.AtomicInteger closeCount
                = new java.util.concurrent.atomic.AtomicInteger();

        ClosingRecordingPackWrapper(ResourcePack inner) { this.inner = inner; }
        int closeCount() { return closeCount.get(); }

        @Override public java.util.Optional<java.io.InputStream> getResource(String path) { return inner.getResource(path); }
        @Override public boolean hasResource(String path) { return inner.hasResource(path); }
        @Override public java.util.Set<String> listResources(String prefix) { return inner.listResources(prefix); }
        @Override public net.aerh.tessera.api.resource.PackMetadata metadata() { return inner.metadata(); }

        @Override
        public void close() throws IOException {
            closeCount.incrementAndGet();
            // Don't double-close inner - the outer try-with-resources already handles it.
        }
    }
}
