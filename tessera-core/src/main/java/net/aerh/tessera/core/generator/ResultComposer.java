package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.image.Graphics2DFactory;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Utility class that composes multiple {@link GeneratorResult}s into a single image.
 *
 * <p>Layout rules:
 * <ul>
 *   <li>{@link CompositeRequest.Layout#VERTICAL}: images are stacked top-to-bottom with
 *       {@code padding} pixels between them and a {@code 15px} border around the whole canvas.</li>
 *   <li>{@link CompositeRequest.Layout#HORIZONTAL}: images are placed side by side with the
 *       same spacing and border.</li>
 * </ul>
 *
 * <p>If any input is animated, all inputs are aligned to the same frame count by looping
 * shorter animations to match the longest. Static inputs are repeated unchanged on every frame.
 */
public final class ResultComposer {

    /** Fixed outer border added on all sides around the composed image. */
    private static final int OUTER_BORDER = 15;

    private ResultComposer() {
        // utility class
    }

    /**
     * Composes the given results into a single {@link GeneratorResult} using a non-GRID
     * layout.
     *
     * <p>Returns a minimal 1x1 transparent image if the result list is empty.
     *
     * @param results the results to compose; must not be {@code null}
     * @param layout the layout direction; must not be {@code null}; must not be
     *                  {@link CompositeRequest.Layout#GRID} (use the 5-arg overload for grid
     *                  layouts)
     * @param padding the pixel gap between adjacent results; must be {@code >= 0}
     * @return a composed static or animated image
     */
    public static GeneratorResult compose(
            List<GeneratorResult> results,
            CompositeRequest.Layout layout,
            int padding) {
        return compose(results, layout, padding, 0, 0);
    }

    /**
     * Composes the given results into a single {@link GeneratorResult}, optionally using
     * GRID layout with the supplied row / column dimensions.
     *
     * <p>For {@link CompositeRequest.Layout#HORIZONTAL} / {@link CompositeRequest.Layout#VERTICAL},
     * the grid dimensions are ignored.
     *
     * @param results the results to compose; must not be {@code null}
     * @param layout the layout direction; must not be {@code null}
     * @param padding the pixel gap between adjacent results; must be {@code >= 0}
     * @param gridRows grid row count (only meaningful for {@link CompositeRequest.Layout#GRID})
     * @param gridCols grid column count (only meaningful for {@link CompositeRequest.Layout#GRID})
     * @return a composed static or animated image
     */
    public static GeneratorResult compose(
            List<GeneratorResult> results,
            CompositeRequest.Layout layout,
            int padding,
            int gridRows,
            int gridCols) {

        if (results.isEmpty()) {
            BufferedImage empty = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            return new GeneratorResult.StaticImage(empty);
        }

        if (layout == CompositeRequest.Layout.GRID) {
            if (gridRows < 1 || gridCols < 1) {
                throw new IllegalArgumentException(
                        "gridRows and gridCols must be >= 1; got rows=" + gridRows + ", cols=" + gridCols);
            }
            boolean anyAnimated = results.stream().anyMatch(GeneratorResult::isAnimated);
            if (anyAnimated) {
                return composeAnimatedGrid(results, padding, gridRows, gridCols);
            }
            List<BufferedImage> frames = results.stream()
                    .map(GeneratorResult::firstFrame)
                    .toList();
            return new GeneratorResult.StaticImage(gridLayout(frames, padding, gridRows, gridCols));
        }

        boolean isVertical = layout == CompositeRequest.Layout.VERTICAL;
        boolean anyAnimated = results.stream().anyMatch(GeneratorResult::isAnimated);

        if (anyAnimated) {
            return composeAnimated(results, isVertical, padding);
        }
        return composeStatic(results, isVertical, padding);
    }

    private static GeneratorResult composeStatic(
            List<GeneratorResult> results, boolean vertical, int padding) {

        List<BufferedImage> frames = results.stream()
                .map(GeneratorResult::firstFrame)
                .toList();

        BufferedImage composed = compositeFrames(frames, vertical, padding);
        return new GeneratorResult.StaticImage(composed);
    }

    private static GeneratorResult composeAnimated(
            List<GeneratorResult> results, boolean vertical, int padding) {

        int maxFrames = 1;
        int frameDelayMs = 33;
        for (GeneratorResult r : results) {
            if (r instanceof GeneratorResult.AnimatedImage anim) {
                maxFrames = Math.max(maxFrames, anim.frames().size());
                frameDelayMs = anim.frameDelayMs();
            }
        }

        BufferedImage[] outputArray = new BufferedImage[maxFrames];

        IntStream.range(0, maxFrames).parallel().forEach(f -> {
            List<BufferedImage> slice = results.stream()
                    .map(r -> frameAt(r, f))
                    .toList();
            outputArray[f] = compositeFrames(slice, vertical, padding);
        });

        return new GeneratorResult.AnimatedImage(List.of(outputArray), frameDelayMs);
    }

    /**
     * Returns the frame at the given index, looping if the result has fewer frames.
     */
    private static BufferedImage frameAt(GeneratorResult result, int index) {
        if (result instanceof GeneratorResult.AnimatedImage anim) {
            return anim.frames().get(index % anim.frames().size());
        }
        return result.firstFrame();
    }

    /**
     * Composites a list of images into a single image using the specified layout.
     */
    private static BufferedImage compositeFrames(
            List<BufferedImage> images, boolean vertical, int padding) {

        int totalW, totalH;
        if (vertical) {
            totalW = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(0);
            int innerH = images.stream().mapToInt(BufferedImage::getHeight).sum();
            int gaps = Math.max(0, images.size() - 1) * padding;
            totalH = innerH + gaps;
        } else {
            int innerW = images.stream().mapToInt(BufferedImage::getWidth).sum();
            int gaps = Math.max(0, images.size() - 1) * padding;
            totalW = innerW + gaps;
            totalH = images.stream().mapToInt(BufferedImage::getHeight).max().orElse(0);
        }

        int canvasW = totalW + OUTER_BORDER * 2;
        int canvasH = totalH + OUTER_BORDER * 2;

        BufferedImage canvas = new BufferedImage(
                Math.max(1, canvasW), Math.max(1, canvasH), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = Graphics2DFactory.createGraphics(canvas);

        int cursor = 0;
        for (BufferedImage img : images) {
            if (vertical) {
                // Center horizontally within the total width
                int xOffset = (totalW - img.getWidth()) / 2;
                g.drawImage(img, OUTER_BORDER + xOffset, OUTER_BORDER + cursor, null);
                cursor += img.getHeight() + padding;
            } else {
                // Center vertically within the total height
                int yOffset = (totalH - img.getHeight()) / 2;
                g.drawImage(img, OUTER_BORDER + cursor, OUTER_BORDER + yOffset, null);
                cursor += img.getWidth() + padding;
            }
        }

        g.dispose();
        return canvas;
    }

    /**
     * Tiles children row-major into a {@code rows x cols} grid at the maximum child-size
     * cell dimension, with {@code padding} pixels between adjacent cells.
     *
     * <p>If {@code children.size() < rows * cols}, the trailing cells are left transparent.
     * Excess children past {@code rows * cols} are ignored.
     */
    private static BufferedImage gridLayout(
            List<BufferedImage> children, int padding, int rows, int cols) {
        int cellW = children.stream().mapToInt(BufferedImage::getWidth).max().orElse(0);
        int cellH = children.stream().mapToInt(BufferedImage::getHeight).max().orElse(0);
        if (cellW == 0 || cellH == 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        int totalW = cellW * cols + padding * Math.max(0, cols - 1);
        int totalH = cellH * rows + padding * Math.max(0, rows - 1);

        BufferedImage out = new BufferedImage(
                Math.max(1, totalW), Math.max(1, totalH), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Graphics2DFactory.createGraphics(out);
        try {
            int cellCount = rows * cols;
            for (int i = 0; i < children.size() && i < cellCount; i++) {
                int row = i / cols;
                int col = i % cols;
                int x = col * (cellW + padding);
                int y = row * (cellH + padding);
                g.drawImage(children.get(i), x, y, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static GeneratorResult composeAnimatedGrid(
            List<GeneratorResult> results, int padding, int rows, int cols) {

        int maxFrames = 1;
        int frameDelayMs = 33;
        for (GeneratorResult r : results) {
            if (r instanceof GeneratorResult.AnimatedImage anim) {
                maxFrames = Math.max(maxFrames, anim.frames().size());
                frameDelayMs = anim.frameDelayMs();
            }
        }

        BufferedImage[] outputArray = new BufferedImage[maxFrames];
        java.util.stream.IntStream.range(0, maxFrames).parallel().forEach(f -> {
            List<BufferedImage> slice = results.stream()
                    .map(r -> frameAt(r, f))
                    .toList();
            outputArray[f] = gridLayout(slice, padding, rows, cols);
        });

        return new GeneratorResult.AnimatedImage(List.of(outputArray), frameDelayMs);
    }
}
