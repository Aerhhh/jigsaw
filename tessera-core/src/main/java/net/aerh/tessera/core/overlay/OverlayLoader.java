package net.aerh.tessera.core.overlay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.overlay.ColorMode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads and caches overlay resources from JSON configuration files and a spritesheet.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Loading the overlay spritesheet ({@code overlays.png})</li>
 *   <li>Loading overlay coordinates from JSON to extract individual overlay textures</li>
 *   <li>Loading color options from JSON</li>
 *   <li>Loading item-to-overlay bindings from JSON</li>
 * </ul>
 *
 * <p>Use {@link #fromDefaults()} to obtain a pre-loaded instance backed by the bundled resources.
 */
public final class OverlayLoader {

    private static final int DEFAULT_IMAGE_SIZE = 128;
    private static final String DEFAULT_RESOURCE_BASE = "/minecraft/assets";

    private final Map<String, ItemOverlayData> itemOverlays;
    private final Map<String, ColorOptionsEntry> colorOptionsMap;

    private OverlayLoader(Map<String, ItemOverlayData> itemOverlays,
                          Map<String, ColorOptionsEntry> colorOptionsMap) {
        this.itemOverlays = itemOverlays;
        this.colorOptionsMap = colorOptionsMap;
    }

    /**
     * @deprecated since 0.3.0 - the classpath-bundled overlay spritesheet was stripped from
     * the published jars; prefer {@link #fromAssetProvider(AssetProvider, String)} which reads
     * from the on-disk asset cache the consumer hydrates via {@code TesseraAssets.fetch(...)}.
     * Hard-removed in a post-1.0 housekeeping pass once all in-tree callers are migrated.
     *
     * @return a loader backed by the now-absent classpath bytes (construction throws
     *         {@link IllegalStateException})
     */
    @Deprecated(since = "0.3.0", forRemoval = true)
    public static OverlayLoader fromDefaults() {
        return load(DEFAULT_RESOURCE_BASE);
    }

    /**
     * Creates an {@link OverlayLoader} rooted at {@code provider.resolveAssetRoot(mcVer)}.
     * Defers to {@link #fromAssetRoot(Path)} for the actual I/O.
     *
     * @param provider the {@link AssetProvider} for the active Minecraft version
     * @param mcVer the Minecraft version string
     * @return a fully loaded overlay loader
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalStateException if any required file cannot be found or parsed
     */
    public static OverlayLoader fromAssetProvider(AssetProvider provider, String mcVer) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(mcVer, "mcVer must not be null");
        return fromAssetRoot(provider.resolveAssetRoot(mcVer));
    }

    /**
     * Returns a new {@link OverlayLoader} loaded from the supplied asset root directory.
     *
     * <p>Expected layout under {@code assetRoot}:
     * <ul>
     *   <li>{@code spritesheets/overlays.png}</li>
     *   <li>{@code json/overlay_colors.json}</li>
     *   <li>{@code json/overlay_coordinates.json}</li>
     *   <li>{@code json/item_overlay_binding.json}</li>
     * </ul>
     *
     * @param assetRoot the resolved asset cache root for the active Minecraft version
     * @return a fully loaded overlay loader
     * @throws NullPointerException if {@code assetRoot} is null
     * @throws IllegalStateException if any required file cannot be found or parsed
     */
    public static OverlayLoader fromAssetRoot(Path assetRoot) {
        Objects.requireNonNull(assetRoot, "assetRoot must not be null");
        Gson gson = new Gson();
        try {
            BufferedImage spriteSheet = loadImageFromPath(assetRoot.resolve("spritesheets/overlays.png"));
            Map<String, ColorOptionsEntry> colorOptions = loadColorOptionsFromPath(
                    assetRoot.resolve("json/overlay_colors.json"), gson);
            Map<String, OverlayDefinition> overlays = loadOverlayCoordinatesFromPath(
                    assetRoot.resolve("json/overlay_coordinates.json"), gson, spriteSheet);
            Map<String, ItemOverlayData> itemOverlays = loadItemBindingsFromPath(
                    assetRoot.resolve("json/item_overlay_binding.json"), gson, overlays, colorOptions);

            return new OverlayLoader(Collections.unmodifiableMap(itemOverlays),
                    Collections.unmodifiableMap(colorOptions));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load overlay resources from " + assetRoot, e);
        }
    }

    /**
     * Supplies an {@link InputStream} for a JSON / PNG resource. Abstracts over classpath
     * and filesystem sources so the core parsers in {@link #parseColorOptions},
     * {@link #parseOverlayCoordinates}, and {@link #parseItemBindings} can be shared.
     */
    @FunctionalInterface
    private interface InputStreamSupplier {
        InputStream open() throws IOException;
    }

    private static BufferedImage loadImage(InputStreamSupplier source, String label) throws IOException {
        try (InputStream stream = source.open()) {
            if (stream == null) {
                throw new IOException("Resource not found: " + label);
            }
            return ImageIO.read(stream);
        }
    }

    private static BufferedImage loadImageFromPath(Path path) throws IOException {
        return loadImage(() -> Files.newInputStream(path), path.toString());
    }

    private static Map<String, ColorOptionsEntry> loadColorOptionsFromPath(Path path, Gson gson)
            throws IOException {
        return parseColorOptions(() -> Files.newInputStream(path), gson, path.toString());
    }

    private static Map<String, OverlayDefinition> loadOverlayCoordinatesFromPath(
            Path path, Gson gson, BufferedImage spriteSheet) throws IOException {
        return parseOverlayCoordinates(() -> Files.newInputStream(path), gson, spriteSheet, path.toString());
    }

    private static Map<String, ItemOverlayData> loadItemBindingsFromPath(
            Path path, Gson gson,
            Map<String, OverlayDefinition> overlays,
            Map<String, ColorOptionsEntry> colorOptions) throws IOException {
        return parseItemBindings(() -> Files.newInputStream(path), gson, overlays, colorOptions, path.toString());
    }

    // --- Shared parsers (: consume any {@link InputStreamSupplier}) ---

    private static Map<String, ColorOptionsEntry> parseColorOptions(
            InputStreamSupplier source, Gson gson, String label) throws IOException {
        try (InputStream stream = source.open()) {
            if (stream == null) {
                throw new IOException("Resource not found: " + label);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonArray array = gson.fromJson(reader, JsonArray.class);
                Map<String, ColorOptionsEntry> result = new HashMap<>();
                for (JsonElement element : array) {
                    JsonObject obj = element.getAsJsonObject();
                    String name = obj.get("name").getAsString();

                    Map<String, int[]> options = new HashMap<>();
                    if (obj.has("options") && obj.get("options").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("options").entrySet()) {
                            JsonArray colorArr = e.getValue().getAsJsonArray();
                            int[] colors = new int[colorArr.size()];
                            for (int i = 0; i < colorArr.size(); i++) {
                                colors[i] = colorArr.get(i).getAsInt();
                            }
                            options.put(e.getKey(), colors);
                        }
                    }

                    boolean allowHex = obj.has("allowHexColors") && obj.get("allowHexColors").getAsBoolean();
                    boolean useDefault = obj.has("useDefaultIfMissing") && obj.get("useDefaultIfMissing").getAsBoolean();

                    int[] defaultColors = null;
                    if (obj.has("defaultColors")) {
                        JsonArray defArr = obj.getAsJsonArray("defaultColors");
                        defaultColors = new int[defArr.size()];
                        for (int i = 0; i < defArr.size(); i++) {
                            defaultColors[i] = defArr.get(i).getAsInt();
                        }
                    }

                    result.put(name, new ColorOptionsEntry(name, options, allowHex, useDefault, defaultColors));
                }
                return result;
            }
        }
    }

    private static Map<String, OverlayDefinition> parseOverlayCoordinates(
            InputStreamSupplier source, Gson gson, BufferedImage spriteSheet, String label)
            throws IOException {
        try (InputStream stream = source.open()) {
            if (stream == null) {
                throw new IOException("Resource not found: " + label);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonArray array = gson.fromJson(reader, JsonArray.class);
                Map<String, OverlayDefinition> result = new HashMap<>();
                for (JsonElement element : array) {
                    JsonObject obj = element.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    // Skip enchant overlays
                    if (name.contains("enchant")) {
                        continue;
                    }
                    int x = obj.get("x").getAsInt();
                    int y = obj.get("y").getAsInt();
                    int size = obj.has("size") && obj.get("size").getAsInt() > 0
                            ? obj.get("size").getAsInt()
                            : DEFAULT_IMAGE_SIZE;
                    String type = obj.has("type") ? obj.get("type").getAsString() : "NORMAL";
                    String colorOptions = obj.has("colorOptions") ? obj.get("colorOptions").getAsString() : null;
                    String colorMode = obj.has("colorMode") ? obj.get("colorMode").getAsString() : null;

                    BufferedImage subImage = spriteSheet.getSubimage(x, y, size, size);
                    result.put(name, new OverlayDefinition(name, subImage, type, colorOptions, colorMode));
                }
                return result;
            }
        }
    }

    private static Map<String, ItemOverlayData> parseItemBindings(
            InputStreamSupplier source, Gson gson,
            Map<String, OverlayDefinition> overlays,
            Map<String, ColorOptionsEntry> colorOptions,
            String label) throws IOException {
        try (InputStream stream = source.open()) {
            if (stream == null) {
                throw new IOException("Resource not found: " + label);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonArray bindings = gson.fromJson(reader, JsonArray.class);
                Map<String, ItemOverlayData> result = new HashMap<>();
                for (JsonElement element : bindings) {
                    JsonObject binding = element.getAsJsonObject();
                    String itemName = binding.get("name").getAsString().toLowerCase();
                    String overlayName = binding.get("overlays").getAsString();
                    OverlayDefinition def = overlays.get(overlayName);
                    if (def == null) {
                        continue;
                    }
                    ColorMode colorMode = "OVERLAY".equalsIgnoreCase(def.colorMode)
                            ? ColorMode.OVERLAY
                            : ColorMode.BASE;
                    // Resolve renderer type: the JSON uses uppercase (NORMAL), our registry uses lowercase
                    String rendererType = def.type.toLowerCase();
                    ColorOptionsEntry colorOpts = def.colorOptionsName != null
                            ? colorOptions.get(def.colorOptionsName)
                            : null;
                    int[] defaultColors = colorOpts != null ? colorOpts.defaultColors : null;
                    boolean allowHex = colorOpts != null && colorOpts.allowHexColors;
                    result.put(itemName, new ItemOverlayData(
                            def.image, colorMode, rendererType, def.colorOptionsName, defaultColors, allowHex));
                }
                return result;
            }
        }
    }

    /**
     * Loads overlay data from the given resource base path.
     *
     * @param resourceBasePath the base classpath prefix (e.g. {@code "/minecraft/assets"})
     * @return a fully loaded overlay loader
     * @throws IllegalStateException if any required resource cannot be found or parsed
     */
    public static OverlayLoader load(String resourceBasePath) {
        Objects.requireNonNull(resourceBasePath, "resourceBasePath must not be null");
        Gson gson = new Gson();

        try {
            // Load spritesheet
            BufferedImage spriteSheet = loadImage(resourceBasePath + "/spritesheets/overlays.png");

            // Load color options
            Map<String, ColorOptionsEntry> colorOptions = loadColorOptions(resourceBasePath, gson);

            // Load overlay coordinates and extract sub-images
            Map<String, OverlayDefinition> overlays = loadOverlayCoordinates(resourceBasePath, gson, spriteSheet);

            // Load item bindings and build the final item overlay map
            Map<String, ItemOverlayData> itemOverlays = loadItemBindings(resourceBasePath, gson, overlays, colorOptions);

            return new OverlayLoader(Collections.unmodifiableMap(itemOverlays),
                    Collections.unmodifiableMap(colorOptions));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load overlay resources from " + resourceBasePath, e);
        }
    }

    /**
     * Returns the overlay data for the given item ID, or empty if the item has no overlay.
     *
     * @param itemId the item ID (case-insensitive)
     * @return an {@link Optional} containing the overlay data, or empty if none exists
     */
    public Optional<ItemOverlayData> getOverlay(String itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        return Optional.ofNullable(itemOverlays.get(itemId.toLowerCase()));
    }

    /**
     * Returns {@code true} if the given item has an overlay binding.
     *
     * @param itemId the item ID (case-insensitive)
     * @return whether an overlay exists for the item
     */
    public boolean hasOverlay(String itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        return itemOverlays.containsKey(itemId.toLowerCase());
    }

    /**
     * Returns all available color option names across all overlay categories,
     * suitable for autocomplete suggestions.
     *
     * @return an unmodifiable set of all named color keys
     */
    public Set<String> getAllColorOptionNames() {
        return colorOptionsMap.values()
                .stream()
                .flatMap(entry -> entry.optionNames().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolves a named color or hex string to a packed RGB integer for the given color category.
     *
     * <p>Checks predefined option names first, then attempts hex parsing if the category allows it.
     * Falls back to the default color if configured.
     *
     * @param category the color options category (e.g. {@code "leather_armor"})
     * @param colorNameOrHex the color name or hex string
     * @return an {@link Optional} containing the resolved RGB value, or empty if not resolvable
     */
    public Optional<Integer> resolveColor(String category, String colorNameOrHex) {
        ColorOptionsEntry entry = colorOptionsMap.get(category);
        if (entry == null) {
            return Optional.empty();
        }
        return entry.resolve(colorNameOrHex);
    }

    // ---- Classpath loading adapters (: delegate to shared parsers) ----

    private static BufferedImage loadImage(String path) throws IOException {
        return loadImage(() -> OverlayLoader.class.getResourceAsStream(path), path);
    }

    private static Map<String, ColorOptionsEntry> loadColorOptions(String basePath, Gson gson) throws IOException {
        String path = basePath + "/json/overlay_colors.json";
        return parseColorOptions(() -> OverlayLoader.class.getResourceAsStream(path), gson, path);
    }

    private static Map<String, OverlayDefinition> loadOverlayCoordinates(
            String basePath, Gson gson, BufferedImage spriteSheet) throws IOException {
        String path = basePath + "/json/overlay_coordinates.json";
        return parseOverlayCoordinates(() -> OverlayLoader.class.getResourceAsStream(path),
                gson, spriteSheet, path);
    }

    private static Map<String, ItemOverlayData> loadItemBindings(
            String basePath, Gson gson,
            Map<String, OverlayDefinition> overlays,
            Map<String, ColorOptionsEntry> colorOptions) throws IOException {
        String path = basePath + "/json/item_overlay_binding.json";
        return parseItemBindings(() -> OverlayLoader.class.getResourceAsStream(path),
                gson, overlays, colorOptions, path);
    }

    // ---- Internal data classes ----

    /**
     * Parsed overlay coordinate definition from overlay_coordinates.json.
     */
    private record OverlayDefinition(
            String name,
            BufferedImage image,
            String type,
            String colorOptionsName,
            String colorMode
    ) {}

    /**
     * Parsed color options entry from overlay_colors.json.
     */
    static final class ColorOptionsEntry {
        final String name;
        final Map<String, int[]> options;
        final boolean allowHexColors;
        final boolean useDefaultIfMissing;
        final int[] defaultColors;

        ColorOptionsEntry(String name, Map<String, int[]> options,
                          boolean allowHexColors, boolean useDefaultIfMissing, int[] defaultColors) {
            this.name = name;
            this.options = Collections.unmodifiableMap(options);
            this.allowHexColors = allowHexColors;
            this.useDefaultIfMissing = useDefaultIfMissing;
            this.defaultColors = defaultColors;
        }

        Set<String> optionNames() {
            return options.keySet();
        }

        /**
         * Resolves a color name or hex string to a packed RGB integer.
         * Returns the first color in the array for named options.
         */
        Optional<Integer> resolve(String nameOrHex) {
            if (nameOrHex == null || nameOrHex.isBlank()) {
                return useDefaultIfMissing && defaultColors != null && defaultColors.length > 0
                        ? Optional.of(defaultColors[0])
                        : Optional.empty();
            }

            String key = nameOrHex.toLowerCase();

            // Check predefined options
            int[] colors = options.get(key);
            if (colors != null && colors.length > 0) {
                return Optional.of(colors[0]);
            }

            // Try hex parsing
            if (allowHexColors) {
                try {
                    String cleaned = key.startsWith("#") ? key.substring(1) : key;
                    cleaned = cleaned.replaceAll("[^a-f0-9]", "");
                    if (!cleaned.isEmpty()) {
                        return Optional.of(Integer.parseInt(cleaned, 16));
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to default
                }
            }

            // Fall back to default
            return useDefaultIfMissing && defaultColors != null && defaultColors.length > 0
                    ? Optional.of(defaultColors[0])
                    : Optional.empty();
        }
    }
}
