package net.aerh.tessera.core.sprite;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.image.Graphics2DFactory;
import net.aerh.tessera.api.sprite.SpriteProvider;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A {@link SpriteProvider} that reads sprites from a packed texture atlas image paired with
 * a JSON file that describes each sprite's position and size.
 *
 * <p>Sprites are extracted lazily on first access and then cached in a {@link ConcurrentHashMap}.
 *
 * @see SpriteProvider
 */
public class AtlasSpriteProvider implements SpriteProvider {

    private static final Gson GSON = new Gson();

    private static final String DEFAULT_ATLAS_IMAGE = "minecraft/assets/spritesheets/minecraft_texture_atlas.png";
    private static final String DEFAULT_ATLAS_COORDINATES = "minecraft/assets/json/atlas_coordinates.json";

    /** Relative path of the atlas image under the {@link Path assetRoot} supplied at construction. */
    private static final String RELATIVE_ATLAS_IMAGE = "spritesheets/minecraft_texture_atlas.png";

    /** Relative path of the atlas coordinates JSON under the {@link Path assetRoot} supplied at construction. */
    private static final String RELATIVE_ATLAS_COORDINATES = "json/atlas_coordinates.json";

    private final BufferedImage atlas;
    private final Map<String, ImageCoordinates> coordinates;
    private final ConcurrentHashMap<String, BufferedImage> cache = new ConcurrentHashMap<>();

    /**
     * @deprecated since 0.3.0 - the classpath-bundled atlas was stripped from the published
     * jars, so this factory will throw {@link IllegalArgumentException} at runtime until its
     * consumers are migrated. Prefer {@link #fromAssetProvider(AssetProvider, String)} which
     * points at the live on-disk atlas that {@code TesseraAtlasBuilder} produces during engine
     * build. Hard-removed in a post-1.0 housekeeping pass once all in-tree callers are migrated.
     *
     * @return a sprite provider backed by the now-absent classpath bytes (construction throws
     *         {@link IllegalArgumentException})
     */
    @Deprecated(since = "0.3.0", forRemoval = true)
    public static AtlasSpriteProvider fromDefaults() {
        return new AtlasSpriteProvider(DEFAULT_ATLAS_IMAGE, DEFAULT_ATLAS_COORDINATES);
    }

    /**
     * Creates an {@link AtlasSpriteProvider} rooted at the atlas PNG + coordinates JSON that
     * {@code TesseraAtlasBuilder} writes into the asset cache during engine build.
     *
     * <p>Reads from:
     * <ul>
     *   <li>{@code <cacheRoot>/tessera/atlas/item_atlas.png}</li>
     *   <li>{@code <cacheRoot>/tessera/atlas/item_coordinates.json}</li>
     * </ul>
     * where {@code cacheRoot = provider.resolveAssetRoot(mcVer)}.
     *
     * @param provider the asset provider for the active Minecraft version
     * @param mcVer the Minecraft version string (must match {@code provider.supportedVersions()})
     * @return a fully loaded atlas sprite provider
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalStateException if the atlas PNG or coordinates JSON cannot be read
     */
    public static AtlasSpriteProvider fromAssetProvider(AssetProvider provider, String mcVer) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(mcVer, "mcVer must not be null");
        Path cacheRoot = provider.resolveAssetRoot(mcVer);
        return new AtlasSpriteProvider(cacheRoot.resolve("tessera/atlas"),
                "item_atlas.png", "item_coordinates.json");
    }

    /**
     * Constructor for atlases produced by {@code TesseraAtlasBuilder}: {x,y,w,h}-keyed JSON
     * object with alphabetically sorted sprite ids (the historical-Jigsaw schema used a
     * top-level array of {name, x, y, size}, which the two-arg {@link #AtlasSpriteProvider(Path)}
     * constructor still reads).
     *
     * @param atlasDir directory containing the atlas PNG + coordinates JSON
     * @param atlasPngFileName filename of the atlas PNG (e.g. {@code item_atlas.png})
     * @param coordinatesFileName filename of the coordinates JSON (e.g. {@code item_coordinates.json})
     */
    AtlasSpriteProvider(Path atlasDir, String atlasPngFileName, String coordinatesFileName) {
        Objects.requireNonNull(atlasDir, "atlasDir must not be null");
        Objects.requireNonNull(atlasPngFileName, "atlasPngFileName must not be null");
        Objects.requireNonNull(coordinatesFileName, "coordinatesFileName must not be null");
        Path atlasImage = atlasDir.resolve(atlasPngFileName);
        Path atlasCoords = atlasDir.resolve(coordinatesFileName);
        this.atlas = loadAtlasFromPath(atlasImage);
        this.coordinates = loadCoordinatesFromKeyedJson(atlasCoords);
    }

    private static Map<String, ImageCoordinates> loadCoordinatesFromKeyedJson(Path coordsJson) {
        return loadKeyedSchemaCoordinates(() -> Files.newInputStream(coordsJson), coordsJson.toString());
    }

    /**
     * Loads the TesseraAtlasBuilder keyed-JSON schema: a top-level JSON object whose keys
     * are sprite ids and values are {@code {x, y, w, h}} boxes. Used by the
     * {@code (atlasDir, pngName, coordsName)} ctor only.
     */
    private static Map<String, ImageCoordinates> loadKeyedSchemaCoordinates(
            InputStreamSupplier source, String label) {
        try (InputStream is = source.open()) {
            if (is == null) {
                throw new IllegalArgumentException("Atlas coordinates not found: " + label);
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                Map<String, ImageCoordinates> result = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    JsonObject box = entry.getValue().getAsJsonObject();
                    int x = box.get("x").getAsInt();
                    int y = box.get("y").getAsInt();
                    // box may have {w, h} or {size}; TesseraAtlasBuilder writes {w, h}. For
                    // 16x16 sprites w == h, so we use w as the single size.
                    int size = box.has("w") ? box.get("w").getAsInt() : box.get("size").getAsInt();
                    result.put(entry.getKey(), new ImageCoordinates(entry.getKey(), x, y, size));
                }
                return result;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read atlas coordinates from " + label, e);
        }
    }

    /**
     * Creates an {@link AtlasSpriteProvider} reading from the supplied asset root directory.
     *
     * <p>Expected layout under {@code assetRoot}:
     * <ul>
     *   <li>{@code spritesheets/minecraft_texture_atlas.png} - the packed atlas image</li>
     *   <li>{@code json/atlas_coordinates.json} - the atlas coordinate map</li>
     * </ul>
     *
     * <p>TODO: atlas-from-client.jar pipeline. Until then, {@code assetRoot.resolve("atlas/items.png")}
     * will NOT exist when assetRoot points at the piston-meta-sourced 26.1.2 cache. Render
     * tests that hit AtlasSpriteProvider are {@code @Disabled} until the atlas pipeline lands.
     *
     * @param assetRoot the resolved asset cache root for the active Minecraft version
     * @throws NullPointerException if {@code assetRoot} is null
     * @throws IllegalStateException if the atlas image or coordinates cannot be read
     */
    public AtlasSpriteProvider(Path assetRoot) {
        Objects.requireNonNull(assetRoot, "assetRoot must not be null");
        Path atlasImage = assetRoot.resolve(RELATIVE_ATLAS_IMAGE);
        Path atlasCoords = assetRoot.resolve(RELATIVE_ATLAS_COORDINATES);

        this.atlas = loadAtlasFromPath(atlasImage);
        this.coordinates = loadCoordinatesFromPath(atlasCoords);
    }

    /**
     * Creates an {@link AtlasSpriteProvider} from the given classpath resources.
     *
     * @param atlasImagePath classpath path to the packed PNG atlas image
     * @param coordinatesJsonPath classpath path to the JSON coordinates file
     */
    public AtlasSpriteProvider(String atlasImagePath, String coordinatesJsonPath) {
        Objects.requireNonNull(atlasImagePath, "atlasImagePath must not be null");
        Objects.requireNonNull(coordinatesJsonPath, "coordinatesJsonPath must not be null");

        this.atlas = loadAtlas(atlasImagePath);
        this.coordinates = loadCoordinates(coordinatesJsonPath);
    }

    /**
     * Abstracts over classpath / filesystem sources so the canonical loaders
     * ({@link #loadAtlasImage}, {@link #loadArraySchemaCoordinates},
     * {@link #loadKeyedSchemaCoordinates}) can be shared across the three entry points
     *. Returning {@code null} signals "resource absent" and is translated into an
     * {@link IllegalArgumentException} at the adapter boundary.
     */
    @FunctionalInterface
    private interface InputStreamSupplier {
        InputStream open() throws IOException;
    }

    // --- Canonical loaders (: one impl each; no classpath/path duplication) ---

    private static BufferedImage loadAtlasImage(InputStreamSupplier source, String label) {
        try (InputStream is = source.open()) {
            if (is == null) {
                throw new IllegalArgumentException("Atlas image not found: " + label);
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read atlas image from " + label, e);
        }
    }

    /**
     * Loads the historical Jigsaw array-schema: a top-level JSON array of
     * {@code {name, x, y, size}} objects. Used by the assetRoot + classpath ctors.
     */
    private static Map<String, ImageCoordinates> loadArraySchemaCoordinates(
            InputStreamSupplier source, String label) {
        try (InputStream is = source.open()) {
            if (is == null) {
                throw new IllegalArgumentException("Atlas coordinates not found: " + label);
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                ImageCoordinates[] entries = GSON.fromJson(reader, ImageCoordinates[].class);
                return Arrays.stream(entries)
                        .collect(Collectors.toMap(ImageCoordinates::name, c -> c));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read atlas coordinates from " + label, e);
        }
    }

    // --- Path-based adapters (shared canonical loaders above) ---

    private static BufferedImage loadAtlasFromPath(Path atlasImage) {
        return loadAtlasImage(() -> Files.newInputStream(atlasImage), atlasImage.toString());
    }

    private static Map<String, ImageCoordinates> loadCoordinatesFromPath(Path coordsJson) {
        return loadArraySchemaCoordinates(() -> Files.newInputStream(coordsJson), coordsJson.toString());
    }

    // --- Classpath adapters (shared canonical loaders above) ---

    private static BufferedImage loadAtlas(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return loadAtlasImage(() -> cl.getResourceAsStream(path), path);
    }

    private static Map<String, ImageCoordinates> loadCoordinates(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return loadArraySchemaCoordinates(() -> cl.getResourceAsStream(path), path);
    }

    /**
     * Returns the sprite for the given texture ID, extracting it from the atlas if not yet cached.
     *
     * @param textureId the texture identifier to look up; must not be {@code null}
     *
     * @return an {@link Optional} containing the sprite, or empty if not found in the atlas
     */
    @Override
    public Optional<BufferedImage> getSprite(String textureId) {
        Objects.requireNonNull(textureId, "textureId must not be null");
        ImageCoordinates coord = coordinates.get(textureId);
        if (coord == null) {
            return Optional.empty();
        }
        return Optional.of(cache.computeIfAbsent(textureId, id -> extractSprite(coord)));
    }

    /**
     * Returns an immutable snapshot of all known sprite texture IDs.
     *
     * @return the set of available texture IDs
     */
    @Override
    public Collection<String> availableSprites() {
        return List.copyOf(coordinates.keySet());
    }

    /**
     * Returns the first sprite whose texture ID contains the given query string.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return an {@link Optional} containing the first matching sprite, or empty if none found
     */
    @Override
    public Optional<BufferedImage> search(String query) {
        Objects.requireNonNull(query, "query must not be null");
        return coordinates.keySet().stream()
                .filter(name -> name.contains(query))
                .findFirst()
                .flatMap(this::getSprite);
    }

    /**
     * Returns all sprites whose texture ID contains the given query string (case-insensitive),
     * sorted alphabetically by texture ID.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return an alphabetically ordered list of matching name-to-image entries; never {@code null}
     */
    @Override
    public List<Map.Entry<String, BufferedImage>> searchAll(String query) {
        Objects.requireNonNull(query, "query must not be null");
        String lowerQuery = query.toLowerCase();
        return coordinates.keySet().stream()
                .filter(name -> name.toLowerCase().contains(lowerQuery))
                .sorted()
                .map(name -> (Map.Entry<String, BufferedImage>) new AbstractMap.SimpleImmutableEntry<>(
                        name, getSprite(name).orElse(null)))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toList());
    }

    /**
     * Returns a snapshot of every loaded sprite, keyed by texture ID.
     *
     * <p>The returned map is sorted alphabetically by texture ID and is unmodifiable.
     *
     * @return an unmodifiable, alphabetically sorted map of all texture IDs to their images
     */
    @Override
    public Map<String, BufferedImage> getAllSprites() {
        TreeMap<String, BufferedImage> result = new TreeMap<>();
        for (String id : coordinates.keySet()) {
            getSprite(id).ifPresent(img -> result.put(id, img));
        }
        return Collections.unmodifiableMap(result);
    }

    private BufferedImage extractSprite(ImageCoordinates coord) {
        BufferedImage sub = atlas.getSubimage(coord.x(), coord.y(), coord.size(), coord.size());
        BufferedImage copy = new BufferedImage(coord.size(), coord.size(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Graphics2DFactory.createGraphics(copy);
        try {
            g.drawImage(sub, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }
}
