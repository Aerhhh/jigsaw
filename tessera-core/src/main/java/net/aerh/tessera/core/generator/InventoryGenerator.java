package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.effect.EffectContext;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.image.Graphics2DFactory;
import net.aerh.tessera.api.sprite.SpriteProvider;
import net.aerh.tessera.core.effect.EffectPipeline;
import net.aerh.tessera.api.font.MinecraftFontId;
import net.aerh.tessera.core.image.ImageOps;
import net.aerh.tessera.core.util.GraphicsUtil;
import net.aerh.tessera.api.exception.RenderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/**
 * Renders a Minecraft-style inventory container image using the authentic GUI textures and colors.
 *
 * <p>Uses the bundled {@code slot.png} texture for slot backgrounds and draws pixel-perfect
 * Minecraft GUI borders with proper highlight/shadow colors. Title and stack counts use the
 * Minecraft font with drop shadow rendering.
 */
public final class InventoryGenerator implements Generator<InventoryRequest, GeneratorResult> {

    private static final Logger log = LoggerFactory.getLogger(InventoryGenerator.class);

    // Minecraft GUI colors (from the old NerdBot MinecraftInventoryGenerator)
    private static final Color INVENTORY_BACKGROUND = new Color(198, 198, 198);
    private static final Color DARK_BORDER_COLOR = new Color(85, 85, 85);
    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);
    /** Selection-overlay ring colour: bright yellow, fully opaque. */
    private static final Color SELECTION_YELLOW = new Color(255, 225, 0);

    private static final String SLOT_TEXTURE_PATH = "/minecraft/assets/textures/slot.png";

    /** UV coordinates for extracting a single slot tile from the Minecraft chest GUI texture. */
    private static final int GUI_SLOT_X = 7;
    private static final int GUI_SLOT_Y = 17;
    private static final int GUI_SLOT_SIZE = 18;
    private static final String GUI_TEXTURE_PATH = "assets/minecraft/textures/gui/container/generic_54.png";

    private final SpriteProvider spriteProvider;
    private final EffectPipeline effectPipeline;
    private final net.aerh.tessera.api.font.FontRegistry fontRegistry;
    private final BufferedImage baseSlotTexture;
    private final int cachedSpriteSize;
    private final ConcurrentHashMap<Integer, BufferedImage> slotTextureCache = new ConcurrentHashMap<>();
    /**
     * Executor used for per-item sprite-loading + effect-pipeline fan-out inside
     * {@link #computePlacedItems}. Injected via the engine so the engine-level
     * concurrency bound applies; test callers that construct an {@code InventoryGenerator}
     * directly fall back to {@link ForkJoinPool#commonPool()} via the legacy ctors.
     */
    private final Executor executor;

    /**
     * Canonical constructor.
     *
     * @param spriteProvider the sprite provider to load item textures from; must not be {@code null}
     * @param effectPipeline the pipeline of effects to apply per item; must not be {@code null}
     * @param slotTexture the base slot texture to use, or {@code null} to use the bundled default
     * @param fontRegistry the font registry to resolve fonts from; must not be {@code null}
     * @param executor the executor used for parallel item computation; must not be {@code null}.
     *                       Engine callers pass the per-engine {@code BoundedVirtualExecutor};
     *                       test callers using the legacy 3- / 4-arg ctors get {@link ForkJoinPool#commonPool()}.
     */
    public InventoryGenerator(SpriteProvider spriteProvider, EffectPipeline effectPipeline,
                              BufferedImage slotTexture,
                              net.aerh.tessera.api.font.FontRegistry fontRegistry,
                              Executor executor) {
        this.spriteProvider = Objects.requireNonNull(spriteProvider, "spriteProvider must not be null");
        this.effectPipeline = Objects.requireNonNull(effectPipeline, "effectPipeline must not be null");
        this.fontRegistry = Objects.requireNonNull(fontRegistry, "fontRegistry must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.baseSlotTexture = slotTexture != null ? slotTexture : loadDefaultSlotTexture();
        this.cachedSpriteSize = computeSpriteSize();
    }

    /**
     * Legacy constructor retained for test callers; delegates to the canonical ctor with
     * {@link ForkJoinPool#commonPool()} as the fan-out executor. Engine callers MUST use
     * the 5-arg ctor to route through the per-engine bounded executor.
     *
     * @param spriteProvider the sprite provider to load item textures from; must not be {@code null}
     * @param effectPipeline the pipeline of effects to apply per item; must not be {@code null}
     * @param slotTexture the base slot texture to use, or {@code null} to use the bundled default
     * @param fontRegistry the font registry to resolve fonts from; must not be {@code null}
     */
    public InventoryGenerator(SpriteProvider spriteProvider, EffectPipeline effectPipeline,
                              BufferedImage slotTexture,
                              net.aerh.tessera.api.font.FontRegistry fontRegistry) {
        this(spriteProvider, effectPipeline, slotTexture, fontRegistry, ForkJoinPool.commonPool());
    }

    /**
     * Legacy constructor retained for test callers. Delegates to the canonical ctor with
     * {@link ForkJoinPool#commonPool()} and the bundled default slot texture.
     *
     * @param spriteProvider the sprite provider to load item textures from; must not be {@code null}
     * @param effectPipeline the pipeline of effects to apply per item; must not be {@code null}
     * @param fontRegistry the font registry to resolve fonts from; must not be {@code null}
     */
    public InventoryGenerator(SpriteProvider spriteProvider, EffectPipeline effectPipeline,
                              net.aerh.tessera.api.font.FontRegistry fontRegistry) {
        this(spriteProvider, effectPipeline, null, fontRegistry, ForkJoinPool.commonPool());
    }

    /**
     * Extracts a slot texture from a resource pack's GUI container texture.
     * Returns {@code null} if the pack doesn't contain the texture.
     *
     * @param pack the resource pack to extract from
     * @return the 18x18 slot texture, or {@code null} if not found
     */
    public static BufferedImage extractSlotTextureFromPack(net.aerh.tessera.api.resource.ResourcePack pack) {
        try {
            Optional<InputStream> streamOpt = pack.getResource(GUI_TEXTURE_PATH);
            if (streamOpt.isEmpty()) {
                return null;
            }
            try (InputStream stream = streamOpt.get()) {
                BufferedImage guiTexture = ImageIO.read(stream);
                if (guiTexture == null || guiTexture.getWidth() < GUI_SLOT_X + GUI_SLOT_SIZE
                        || guiTexture.getHeight() < GUI_SLOT_Y + GUI_SLOT_SIZE) {
                    return null;
                }
                return guiTexture.getSubimage(GUI_SLOT_X, GUI_SLOT_Y, GUI_SLOT_SIZE, GUI_SLOT_SIZE);
            }
        } catch (IOException e) {
            log.warn("Failed to extract slot texture from resource pack: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public GeneratorResult render(InventoryRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        // Derive scale factor from sprite size (sprites are double-size in atlas)
        int baseSpriteSize = cachedSpriteSize;
        int baseScaleFactor = Math.max(1, baseSpriteSize / 2 / 16);

        // Apply request scale on top
        int scaleFactor = baseScaleFactor * input.scale();
        int slotSize = 18 * scaleFactor;
        int itemSize = 16 * scaleFactor;

        // Layout calculations (matching old MinecraftInventoryGenerator)
        int borderSize = input.drawBorder() ? 7 * scaleFactor : 0;
        boolean drawTitle = input.drawTitle() && !input.title().isBlank();
        int titleHeight = borderSize + (drawTitle ? 13 * scaleFactor : 0)
                - (input.drawBorder() && drawTitle ? 3 * scaleFactor : 0);

        int imageWidth = (input.slotsPerRow() * slotSize) + (borderSize * 2);
        int imageHeight = (input.rows() * slotSize) + titleHeight + borderSize;

        BufferedImage canvas = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Graphics2DFactory.createGraphics(canvas);

        // Load Minecraft font
        Font mcFont = fontRegistry.getStyledFont(MinecraftFontId.DEFAULT, false, false, scaleFactor * 8f);
        g.setFont(mcFont);

        // Fill background with inventory gray
        if (input.drawBackground()) {
            g.setColor(INVENTORY_BACKGROUND);
            g.fillRect(0, 0, imageWidth, imageHeight);
        }

        // Draw border
        if (input.drawBorder()) {
            drawMinecraftBorder(g, imageWidth, imageHeight, scaleFactor);
        }

        // Draw title
        if (drawTitle) {
            int titleX = 8 * scaleFactor;
            int titleY = titleHeight - scaleFactor * 4;

            // Drop shadow
            g.setColor(DROP_SHADOW_COLOR);
            g.drawString(input.title(), titleX + scaleFactor, titleY + scaleFactor);

            // Foreground
            g.setColor(NORMAL_TEXT_COLOR);
            g.drawString(input.title(), titleX, titleY);
        }

        // Load and resize slot texture
        BufferedImage slotTexture = resizeSlotTexture(slotSize);

        // Draw slots
        int slotsOriginY = titleHeight;

        for (int row = 0; row < input.rows(); row++) {
            for (int col = 0; col < input.slotsPerRow(); col++) {
                int sx = borderSize + (col * slotSize);
                int sy = slotsOriginY + (row * slotSize);

                if (input.drawBackground()) {
                    if (slotTexture != null) {
                        g.drawImage(slotTexture, sx, sy, null);
                    } else {
                        drawSlotFallback(g, sx, sy, slotSize, scaleFactor);
                    }
                }
            }
        }

        // Selection overlay: draw a 2px bright-yellow outline around selected slots.
        if (!input.selectedSlots().isEmpty()) {
            drawSelectionOverlays(g, input, borderSize, slotsOriginY, slotSize, scaleFactor);
        }

        // Parallel phase: compute all PlacedItem data (sprite loading + effect pipeline)
        List<PlacedItem> placed = computePlacedItems(input, borderSize, slotsOriginY, slotSize, itemSize);

        // Sequential phase: draw items onto the shared canvas
        int maxGlintFrames = 0;
        int glintFrameDelay = 33;

        for (PlacedItem pi : placed) {
            if (pi.staticSprite() != null) {
                BufferedImage toDraw = pi.glintFrames() != null ? pi.glintFrames().getFirst() : pi.staticSprite();
                g.drawImage(toDraw, pi.itemX(), pi.itemY(), itemSize, itemSize, null);
            }
            if (pi.glintFrames() != null && !pi.glintFrames().isEmpty()) {
                maxGlintFrames = Math.max(maxGlintFrames, pi.glintFrames().size());
                glintFrameDelay = pi.glintFrameDelay();
            }
        }

        // Draw stack counts on the base canvas
        for (PlacedItem pi : placed) {
            if (pi.item().stackCount() > 1) {
                drawStackCount(g, pi, scaleFactor, mcFont);
            }
        }

        g.dispose();

        // If any items are enchanted, build animated frames for the whole inventory
        if (maxGlintFrames > 0) {
            List<BufferedImage> inventoryFrames = buildAnimationFrames(
                    canvas, placed, itemSize, scaleFactor, mcFont, maxGlintFrames);
            return new GeneratorResult.AnimatedImage(inventoryFrames, glintFrameDelay);
        }

        return new GeneratorResult.StaticImage(canvas);
    }

    @Override
    public Class<InventoryRequest> inputType() {
        return InventoryRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    /**
     * Returns the scale factor derived from the sprite provider's actual sprite size.
     */
    public int getScaleFactor() {
        return Math.max(1, cachedSpriteSize / 2 / 16);
    }

    private int computeSpriteSize() {
        return spriteProvider.availableSprites().stream()
                .findFirst()
                .flatMap(spriteProvider::getSprite)
                .map(BufferedImage::getWidth)
                .orElse(32); // Default to 32 (16 * 2) if no sprites available
    }

    // -------------------------------------------------------------------------
    // Minecraft GUI border (pixel-perfect port from old MinecraftInventoryGenerator)
    // -------------------------------------------------------------------------

    private static void drawMinecraftBorder(Graphics2D g, int imageWidth, int imageHeight, int s) {
        // Background fill
        g.setColor(INVENTORY_BACKGROUND);
        g.fillRect(s * 3, s * 3, imageWidth - s * 6, imageHeight - s * 6);
        g.fillRect(imageWidth - s * 3, s * 2, s, s); // top right corner fill
        g.fillRect(s * 2, imageHeight - s * 3, s, s); // bottom left corner fill

        // Dark gray shadow (right and bottom edges)
        g.setColor(DARK_BORDER_COLOR);
        g.fillRect(imageWidth - s * 3, s * 3, s * 2, imageHeight - s * 4);
        g.fillRect(s * 3, imageHeight - s * 3, imageWidth - s * 6, s * 2);
        g.fillRect(imageWidth - s * 4, imageHeight - s * 4, s, s);

        // White highlight (left and top edges)
        g.setColor(Color.WHITE);
        g.fillRect(s, s, s * 2, imageHeight - s * 4);
        g.fillRect(s * 3, s, imageWidth - s * 6, s * 2);
        g.fillRect(s * 3, s * 3, s, s);

        // Black outline
        g.setColor(Color.BLACK);
        g.fillRect(0, s * 2, s, imageHeight - s * 5);           // left
        g.fillRect(imageWidth - s, s * 3, s, imageHeight - s * 5); // right
        g.fillRect(s * 2, 0, imageWidth - s * 5, s);            // top
        g.fillRect(s * 3, imageHeight - s, imageWidth - s * 5, s); // bottom
        // Corner pixels
        g.fillRect(s, s, s, s);                                   // top-left
        g.fillRect(imageWidth - s * 3, s, s, s);                  // top-right upper
        g.fillRect(imageWidth - s * 2, s * 2, s, s);              // top-right lower
        g.fillRect(imageWidth - s * 2, imageHeight - s * 2, s, s); // bottom-right
        g.fillRect(s, imageHeight - s * 3, s, s);                 // bottom-left upper
        g.fillRect(s * 2, imageHeight - s * 2, s, s);             // bottom-left lower
    }

    // -------------------------------------------------------------------------
    // Slot rendering
    // -------------------------------------------------------------------------

    /**
     * Resizes the base slot texture to the target slot size for rendering.
     * Results are cached by slot size since the same size is used for all slots in a render.
     */
    private BufferedImage resizeSlotTexture(int slotSize) {
        if (baseSlotTexture == null) {
            return null;
        }
        return slotTextureCache.computeIfAbsent(slotSize,
                size -> ImageOps.resize(baseSlotTexture, size, size, BufferedImage.TYPE_INT_ARGB));
    }

    /**
     * Loads the bundled default slot texture from the classpath.
     */
    private static BufferedImage loadDefaultSlotTexture() {
        try (InputStream stream = InventoryGenerator.class.getResourceAsStream(SLOT_TEXTURE_PATH)) {
            if (stream != null) {
                return ImageIO.read(stream);
            }
        } catch (IOException e) {
            log.warn("Failed to load default slot texture: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Overlays a 2-pixel bright-yellow outline around every slot whose index is listed in
     * {@link InventoryRequest#selectedSlots()}. The outline sits above the slot texture
     * but below any item sprite so the selection ring stays visually coherent when
     * stacked with the item that may live inside the slot.
     */
    private static void drawSelectionOverlays(Graphics2D g, InventoryRequest input,
                                              int borderSize, int slotsOriginY, int slotSize, int scaleFactor) {
        int totalSlots = input.rows() * input.slotsPerRow();
        int thickness = Math.max(2, scaleFactor);
        Color saved = g.getColor();
        try {
            g.setColor(SELECTION_YELLOW);
            for (Integer idx : input.selectedSlots()) {
                if (idx == null || idx < 0 || idx >= totalSlots) {
                    continue; // defensive: out-of-range indices are silently ignored.
                }
                int row = idx / input.slotsPerRow();
                int col = idx % input.slotsPerRow();
                int sx = borderSize + (col * slotSize);
                int sy = slotsOriginY + (row * slotSize);
                // Top + bottom edges.
                g.fillRect(sx, sy, slotSize, thickness);
                g.fillRect(sx, sy + slotSize - thickness, slotSize, thickness);
                // Left + right edges (inset to avoid double-drawing corners).
                g.fillRect(sx, sy + thickness, thickness, slotSize - 2 * thickness);
                g.fillRect(sx + slotSize - thickness, sy + thickness, thickness, slotSize - 2 * thickness);
            }
        } finally {
            g.setColor(saved);
        }
    }

    private static void drawSlotFallback(Graphics2D g, int x, int y, int slotSize, int scaleFactor) {
        // Background
        g.setColor(INVENTORY_BACKGROUND);
        g.fillRect(x + scaleFactor, y + scaleFactor, slotSize - 2 * scaleFactor, slotSize - 2 * scaleFactor);

        // Dark border (top-left)
        g.setColor(DARK_BORDER_COLOR);
        g.drawRect(x, y, slotSize - 1, slotSize - 1);
        g.drawRect(x + 1, y + 1, slotSize - 3, slotSize - 3);

        // White highlight (bottom-right)
        g.setColor(Color.WHITE);
        g.drawLine(x + slotSize - 1, y, x + slotSize - 1, y + slotSize - 1);
        g.drawLine(x, y + slotSize - 1, x + slotSize - 1, y + slotSize - 1);
    }

    // -------------------------------------------------------------------------
    // Stack count rendering
    // -------------------------------------------------------------------------

    private static void drawStackCount(Graphics2D g, PlacedItem pi, int scaleFactor, Font font) {
        String text = String.valueOf(pi.item().stackCount());
        g.setFont(font);

        int textWidth = g.getFontMetrics().stringWidth(text);
        int textX = pi.slotX() + pi.slotSize() - textWidth + 1;
        int textY = pi.slotY() + pi.slotSize() - scaleFactor + 1;

        // Drop shadow
        g.setColor(DROP_SHADOW_COLOR);
        g.drawString(text, textX + scaleFactor - 1, textY + scaleFactor - 1);

        // Foreground
        g.setColor(NORMAL_TEXT_COLOR);
        g.drawString(text, textX - 1, textY - 1);
    }

    private record PlacedItem(
            InventoryItem item, int slotX, int slotY, int slotSize, int spriteSize,
            int itemX, int itemY,
            BufferedImage staticSprite, List<BufferedImage> glintFrames, int glintFrameDelay
    ) {}

    // -------------------------------------------------------------------------
    // Parallel item computation
    // -------------------------------------------------------------------------

    /**
     * Computes all {@link PlacedItem} data in parallel. Each item's sprite loading and effect
     * pipeline execution runs concurrently on the per-generator {@link #executor}; items outside
     * the visible grid are filtered out.
     *
     * <p>Uses {@link CompletableFuture#allOf(CompletableFuture...)} fan-out with
     * {@link CompletableFuture#whenComplete(java.util.function.BiConsumer)} for
     * first-failure cancellation. An earlier implementation used the Java 25 preview
     * {@code StructuredTaskScope}; that variant was migrated out to restore Java 21 LTS
     * no-preview compatibility.
     *
     * <p>The executor is injected via the canonical constructor so engine callers route
     * through the engine-owned {@code BoundedVirtualExecutor}. An earlier implementation
     * allocated a fresh unbounded virtual-thread executor per {@code render()} call and
     * leaked it; reusing the engine-owned executor fixes that leak.
     */
    private List<PlacedItem> computePlacedItems(InventoryRequest input, int borderSize,
                                                 int slotsOriginY, int slotSize, int itemSize)
            throws RenderException {
        List<InventoryItem> items = input.items();
        if (items.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<PlacedItem>> futures = new ArrayList<>(items.size());
        for (InventoryItem item : items) {
            CompletableFuture<PlacedItem> f = CompletableFuture.supplyAsync(
                    () -> computeSingleItem(item, input, borderSize, slotsOriginY, slotSize, itemSize),
                    executor);
            futures.add(f);
        }

        // First-failure cancellation: cancel siblings on any failure.
        for (CompletableFuture<PlacedItem> f : futures) {
            f.whenComplete((r, ex) -> {
                if (ex != null) {
                    for (CompletableFuture<PlacedItem> sibling : futures) {
                        if (sibling != f && !sibling.isDone()) {
                            sibling.cancel(true);
                        }
                    }
                }
            });
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<PlacedItem> placed = new ArrayList<>(futures.size());
            for (CompletableFuture<PlacedItem> f : futures) {
                PlacedItem pi = f.join();
                if (pi != null) {
                    placed.add(pi);
                }
            }
            return placed;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RenderException re) throw re;
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new RenderException("Inventory item computation interrupted",
                        java.util.Map.of(), cause);
            }
            throw new RenderException("Inventory item computation failed",
                    java.util.Map.of("cause", String.valueOf(cause.getMessage())), cause);
        }
    }

    /**
     * Computes a single {@link PlacedItem} including sprite loading and effect pipeline execution.
     * Returns {@code null} if the item's slot is outside the visible grid.
     */
    private PlacedItem computeSingleItem(InventoryItem item, InventoryRequest input,
                                          int borderSize, int slotsOriginY,
                                          int slotSize, int itemSize) {
        int slot = item.slot();
        int row = slot / input.slotsPerRow();
        int col = slot % input.slotsPerRow();

        if (row >= input.rows() || col >= input.slotsPerRow()) {
            return null;
        }

        int sx = borderSize + (col * slotSize);
        int sy = slotsOriginY + (row * slotSize);
        int itemPadding = (slotSize - itemSize) / 2;
        int itemX = sx + itemPadding;
        int itemY = sy + itemPadding;

        BufferedImage staticSprite = spriteProvider.getSprite(item.itemId()).orElse(null);
        List<BufferedImage> itemGlintFrames = null;
        int glintFrameDelay = 33;

        if (staticSprite != null && item.enchanted()) {
            EffectContext effectCtx = EffectContext.builder()
                    .image(staticSprite)
                    .itemId(item.itemId())
                    .enchanted(true)
                    .build();
            EffectContext result = effectPipeline.execute(effectCtx);
            if (!result.animationFrames().isEmpty()) {
                itemGlintFrames = result.animationFrames();
                glintFrameDelay = result.frameDelayMs();
            }
        }

        return new PlacedItem(item, sx, sy, slotSize, itemSize, itemX, itemY,
                staticSprite, itemGlintFrames, glintFrameDelay);
    }

    /**
     * Builds full inventory animation frames by compositing the base canvas with each
     * enchanted item's glint frame cycling. Non-enchanted items and stack counts are
     * drawn identically on every frame. Frames are generated in parallel.
     */
    private static List<BufferedImage> buildAnimationFrames(
            BufferedImage baseCanvas, List<PlacedItem> placed, int itemSize,
            int scaleFactor, Font mcFont, int frameCount) {

        BufferedImage[] frameArray = new BufferedImage[frameCount];

        IntStream.range(0, frameCount).parallel().forEach(frameIndex -> {
            BufferedImage frame = new BufferedImage(
                    baseCanvas.getWidth(), baseCanvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D fg = Graphics2DFactory.createGraphics(frame);

            // Draw the base canvas (background, border, slots, non-enchanted items, stack counts)
            fg.drawImage(baseCanvas, 0, 0, null);

            // Overdraw enchanted items with their current glint frame
            for (PlacedItem pi : placed) {
                if (pi.glintFrames() != null && !pi.glintFrames().isEmpty()) {
                    BufferedImage glintFrame = pi.glintFrames().get(frameIndex % pi.glintFrames().size());
                    fg.drawImage(glintFrame, pi.itemX(), pi.itemY(), itemSize, itemSize, null);
                }
            }

            fg.dispose();
            frameArray[frameIndex] = frame;
        });

        return List.of(frameArray);
    }
}
