package net.aerh.tessera.core.engine.assets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD coverage for {@link TesseraAtlasBuilder} deterministic atlas stitching. Fixtures are
 * synthesised in {@link TempDir}-scoped temp dirs so no binary files land in the repo.
 */
class TesseraAtlasBuilderTest {

    private static final int STANDARD_SPRITE = 16;

    @Test
    void empty_texture_dir_throws_illegal_state(@TempDir Path cacheRoot) throws Exception {
        // Directory missing entirely.
        assertThatThrownBy(() -> TesseraAtlasBuilder.buildAtlas(cacheRoot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no textures to stitch");
    }

    @Test
    void empty_item_dir_throws_illegal_state(@TempDir Path cacheRoot) throws Exception {
        Path itemDir = cacheRoot.resolve("assets/minecraft/textures/item");
        Files.createDirectories(itemDir);

        assertThatThrownBy(() -> TesseraAtlasBuilder.buildAtlas(cacheRoot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no textures to stitch");
    }

    @Test
    void single_16x16_item_stitched_to_1x1_atlas(@TempDir Path cacheRoot) throws Exception {
        Path itemDir = cacheRoot.resolve("assets/minecraft/textures/item");
        Files.createDirectories(itemDir);
        writePng(itemDir.resolve("apple.png"), STANDARD_SPRITE, 0xFFFF0000);

        TesseraAtlasBuilder.AtlasBuildResult result = TesseraAtlasBuilder.buildAtlas(cacheRoot);

        assertThat(result.spriteCount()).isEqualTo(1);
        assertThat(Files.exists(result.atlasPng())).isTrue();
        assertThat(Files.exists(result.coordinatesJson())).isTrue();

        BufferedImage atlas = ImageIO.read(result.atlasPng().toFile());
        assertThat(atlas.getWidth()).isEqualTo(STANDARD_SPRITE);
        assertThat(atlas.getHeight()).isEqualTo(STANDARD_SPRITE);
        assertThat(atlas.getRGB(0, 0)).isEqualTo(0xFFFF0000);

        JsonObject coords = readJsonObject(result.coordinatesJson());
        assertThat(coords.has("minecraft:apple")).isTrue();
        JsonObject apple = coords.getAsJsonObject("minecraft:apple");
        assertThat(apple.get("x").getAsInt()).isEqualTo(0);
        assertThat(apple.get("y").getAsInt()).isEqualTo(0);
        assertThat(apple.get("w").getAsInt()).isEqualTo(STANDARD_SPRITE);
        assertThat(apple.get("h").getAsInt()).isEqualTo(STANDARD_SPRITE);
    }

    @Test
    void multiple_16x16_items_deterministic_slot_order(@TempDir Path cacheRoot) throws Exception {
        Path itemDir = cacheRoot.resolve("assets/minecraft/textures/item");
        Files.createDirectories(itemDir);
        writePng(itemDir.resolve("apple.png"),   STANDARD_SPRITE, 0xFFFF0000); // red
        writePng(itemDir.resolve("bow.png"),     STANDARD_SPRITE, 0xFF00FF00); // green
        writePng(itemDir.resolve("coal.png"),    STANDARD_SPRITE, 0xFF0000FF); // blue
        writePng(itemDir.resolve("diamond.png"), STANDARD_SPRITE, 0xFFFFFF00); // yellow

        TesseraAtlasBuilder.AtlasBuildResult result = TesseraAtlasBuilder.buildAtlas(cacheRoot);

        // 4 items => 2x2 grid => 32x32 atlas.
        BufferedImage atlas = ImageIO.read(result.atlasPng().toFile());
        assertThat(atlas.getWidth()).isEqualTo(32);
        assertThat(atlas.getHeight()).isEqualTo(32);

        // Row-major, sorted lexicographically:
        //   apple at (0,0), bow at (16,0), coal at (0,16), diamond at (16,16).
        assertThat(atlas.getRGB(0, 0)).isEqualTo(0xFFFF0000);   // apple center-ish
        assertThat(atlas.getRGB(16, 0)).isEqualTo(0xFF00FF00);  // bow
        assertThat(atlas.getRGB(0, 16)).isEqualTo(0xFF0000FF);  // coal
        assertThat(atlas.getRGB(16, 16)).isEqualTo(0xFFFFFF00); // diamond

        JsonObject coords = readJsonObject(result.coordinatesJson());
        assertThat(coords.getAsJsonObject("minecraft:apple").get("x").getAsInt()).isEqualTo(0);
        assertThat(coords.getAsJsonObject("minecraft:apple").get("y").getAsInt()).isEqualTo(0);
        assertThat(coords.getAsJsonObject("minecraft:bow").get("x").getAsInt()).isEqualTo(16);
        assertThat(coords.getAsJsonObject("minecraft:bow").get("y").getAsInt()).isEqualTo(0);
        assertThat(coords.getAsJsonObject("minecraft:coal").get("x").getAsInt()).isEqualTo(0);
        assertThat(coords.getAsJsonObject("minecraft:coal").get("y").getAsInt()).isEqualTo(16);
        assertThat(coords.getAsJsonObject("minecraft:diamond").get("x").getAsInt()).isEqualTo(16);
        assertThat(coords.getAsJsonObject("minecraft:diamond").get("y").getAsInt()).isEqualTo(16);
    }

    @Test
    void non_16x16_texture_skipped(@TempDir Path cacheRoot) throws Exception {
        Path itemDir = cacheRoot.resolve("assets/minecraft/textures/item");
        Files.createDirectories(itemDir);
        writePng(itemDir.resolve("apple.png"), STANDARD_SPRITE, 0xFFFF0000);
        writePng(itemDir.resolve("weird.png"), 32, 0xFF00FF00);

        TesseraAtlasBuilder.AtlasBuildResult result = TesseraAtlasBuilder.buildAtlas(cacheRoot);

        assertThat(result.spriteCount()).as("weird.png skipped as non-standard size").isEqualTo(1);
        JsonObject coords = readJsonObject(result.coordinatesJson());
        assertThat(coords.has("minecraft:apple")).isTrue();
        assertThat(coords.has("minecraft:weird")).isFalse();
    }

    @Test
    void all_non_standard_throws_illegal_state(@TempDir Path cacheRoot) throws Exception {
        Path itemDir = cacheRoot.resolve("assets/minecraft/textures/item");
        Files.createDirectories(itemDir);
        writePng(itemDir.resolve("big.png"), 32, 0xFFFF0000);

        assertThatThrownBy(() -> TesseraAtlasBuilder.buildAtlas(cacheRoot))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void atlas_output_byte_stable(@TempDir Path cacheRoot1, @TempDir Path cacheRoot2) throws Exception {
        writeBaseFixture(cacheRoot1);
        writeBaseFixture(cacheRoot2);

        TesseraAtlasBuilder.AtlasBuildResult r1 = TesseraAtlasBuilder.buildAtlas(cacheRoot1);
        TesseraAtlasBuilder.AtlasBuildResult r2 = TesseraAtlasBuilder.buildAtlas(cacheRoot2);

        assertThat(sha256(r1.atlasPng())).isEqualTo(sha256(r2.atlasPng()));
        assertThat(sha256(r1.coordinatesJson())).isEqualTo(sha256(r2.coordinatesJson()));
    }

    @Test
    void output_file_paths_match_atlas_sprite_provider_expectations(@TempDir Path cacheRoot) throws Exception {
        writeBaseFixture(cacheRoot);

        TesseraAtlasBuilder.AtlasBuildResult result = TesseraAtlasBuilder.buildAtlas(cacheRoot);

        assertThat(result.atlasPng())
                .isEqualTo(cacheRoot.resolve("tessera/atlas/item_atlas.png"));
        assertThat(result.coordinatesJson())
                .isEqualTo(cacheRoot.resolve("tessera/atlas/item_coordinates.json"));
    }

    @Test
    void null_cache_root_raises_npe() {
        assertThatThrownBy(() -> TesseraAtlasBuilder.buildAtlas(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- helpers ----

    private static void writeBaseFixture(Path cacheRoot) throws Exception {
        Path itemDir = cacheRoot.resolve("assets/minecraft/textures/item");
        Files.createDirectories(itemDir);
        writePng(itemDir.resolve("apple.png"),   STANDARD_SPRITE, 0xFFFF0000);
        writePng(itemDir.resolve("bow.png"),     STANDARD_SPRITE, 0xFF00FF00);
        writePng(itemDir.resolve("coal.png"),    STANDARD_SPRITE, 0xFF0000FF);
    }

    private static void writePng(Path path, int size, int rgbColor) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, y, rgbColor);
            }
        }
        ImageIO.write(img, "png", path.toFile());
    }

    private static JsonObject readJsonObject(Path p) throws Exception {
        try (var in = Files.newInputStream(p)) {
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    private static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(Files.readAllBytes(p));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
