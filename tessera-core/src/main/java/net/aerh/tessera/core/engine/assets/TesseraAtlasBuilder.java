package net.aerh.tessera.core.engine.assets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aerh.tessera.api.image.Graphics2DFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Stitches per-item PNGs extracted from {@code client.jar} into a packed atlas + coordinate
 * JSON that {@link net.aerh.tessera.core.sprite.AtlasSpriteProvider} consumes.
 *
 * <p>Output file layout under {@code cacheRoot}:
 * <ul>
 *   <li>{@code tessera/atlas/item_atlas.png} - packed PNG.</li>
 *   <li>{@code tessera/atlas/item_coordinates.json} - {@code {"minecraft:<id>":{x,y,w,h}}} map
 *       with alphabetically sorted keys.</li>
 * </ul>
 *
 * <p>Deterministic: input files are sorted lexicographically before stitching, the slot grid
 * is row-major, and the coordinate JSON keys are alphabetically sorted. Two runs on
 * byte-identical input directories produce byte-identical {@code item_atlas.png} +
 * {@code item_coordinates.json}.
 *
 * <p>The atlas is drawn via {@link Graphics2DFactory#createGraphics(BufferedImage)} so the four
 * mandatory rendering hints (AA=OFF, TextAA=OFF, FM=OFF, Interp=NN) are applied. This is the
 * canonical pattern every new {@code Graphics2D} call site under {@code net.aerh.tessera..}
 * must follow; the {@code Graphics2DFactoryArchTest} gate enforces it.
 */
public final class TesseraAtlasBuilder {

    private static final Logger log = LoggerFactory.getLogger(TesseraAtlasBuilder.class);
    private static final int STANDARD_SPRITE = 16;

    /** Result of a {@link #buildAtlas(Path)} run - written paths plus sprite count. */
    public record AtlasBuildResult(Path atlasPng, Path coordinatesJson, int spriteCount) {}

    private TesseraAtlasBuilder() {
        /* static-only */
    }

    /**
     * Stitches every {@code 16x16} PNG under {@code cacheRoot/assets/minecraft/textures/item/}
     * into a packed atlas + coordinate JSON. Non-standard-size textures are logged and skipped.
     *
     * @param cacheRoot the on-disk asset cache root for a given MC version
     * @return the output artifact paths plus the number of sprites actually stitched
     * @throws IllegalStateException if the item texture directory is missing, empty, or
     *                               contains no 16x16 PNGs after filtering
     * @throws IOException if disk reads/writes fail
     * @throws NullPointerException if {@code cacheRoot} is {@code null}
     */
    public static AtlasBuildResult buildAtlas(Path cacheRoot) throws IOException {
        Objects.requireNonNull(cacheRoot, "cacheRoot must not be null");

        Path itemTextureRoot = cacheRoot.resolve("assets/minecraft/textures/item");
        if (!Files.isDirectory(itemTextureRoot)) {
            throw new IllegalStateException(
                    "no textures to stitch: " + itemTextureRoot + " is not a directory");
        }

        List<Path> textures;
        try (Stream<Path> stream = Files.list(itemTextureRoot)) {
            textures = stream
                    .filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        if (textures.isEmpty()) {
            throw new IllegalStateException(
                    "no textures to stitch: " + itemTextureRoot + " contains no .png files");
        }

        List<Path> squares = new ArrayList<>();
        List<BufferedImage> images = new ArrayList<>();
        for (Path tex : textures) {
            BufferedImage img = ImageIO.read(tex.toFile());
            if (img == null) {
                log.warn("Skipping unreadable texture: {}", tex);
                continue;
            }
            if (img.getWidth() != STANDARD_SPRITE || img.getHeight() != STANDARD_SPRITE) {
                log.warn("Skipping non-standard-size texture (expected {}x{}, got {}x{}): {}",
                        STANDARD_SPRITE, STANDARD_SPRITE,
                        img.getWidth(), img.getHeight(), tex);
                continue;
            }
            squares.add(tex);
            images.add(img);
        }
        if (squares.isEmpty()) {
            throw new IllegalStateException(
                    "no 16x16 textures to stitch under " + itemTextureRoot + "; all skipped");
        }

        int gridSide = (int) Math.ceil(Math.sqrt(squares.size()));
        int atlasPx = gridSide * STANDARD_SPRITE;
        BufferedImage atlas = new BufferedImage(atlasPx, atlasPx, BufferedImage.TYPE_INT_ARGB);

        TreeMap<String, Map<String, Integer>> coords = new TreeMap<>();
        Graphics2D g = Graphics2DFactory.createGraphics(atlas);
        try {
            for (int i = 0; i < squares.size(); i++) {
                Path tex = squares.get(i);
                BufferedImage img = images.get(i);
                int row = i / gridSide;
                int col = i % gridSide;
                int x = col * STANDARD_SPRITE;
                int y = row * STANDARD_SPRITE;
                g.drawImage(img, x, y, null);

                String spriteId = "minecraft:" + stripPngExt(tex.getFileName().toString());
                // LinkedHashMap with insertion order so the JSON is x,y,w,h per run (purely cosmetic
                // but keeps diffs readable).
                Map<String, Integer> box = new LinkedHashMap<>();
                box.put("x", x);
                box.put("y", y);
                box.put("w", STANDARD_SPRITE);
                box.put("h", STANDARD_SPRITE);
                coords.put(spriteId, box);
            }
        } finally {
            g.dispose();
        }

        Path atlasDir = cacheRoot.resolve("tessera/atlas");
        Files.createDirectories(atlasDir);
        Path atlasPng = atlasDir.resolve("item_atlas.png");
        Path coordsJson = atlasDir.resolve("item_coordinates.json");

        ImageIO.write(atlas, "png", atlasPng.toFile());

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        try (Writer w = Files.newBufferedWriter(coordsJson, StandardCharsets.UTF_8)) {
            gson.toJson(coords, w);
        }

        log.info("Atlas stitched: {} sprites, {}x{} atlas, output at {}",
                squares.size(), atlasPx, atlasPx, atlasPng);
        return new AtlasBuildResult(atlasPng, coordsJson, squares.size());
    }

    private static String stripPngExt(String name) {
        return name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
    }
}
