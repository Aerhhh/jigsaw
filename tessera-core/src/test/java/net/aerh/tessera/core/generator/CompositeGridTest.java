package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.RenderRequest;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for grid layout (grid layout): {@link CompositeRequest.Layout#GRID}
 * tiles children row-major at the maximum-child-size cell dimension.
 */
class CompositeGridTest {

    @Test
    void grid_2x2_places_four_children_in_row_major_order() {
        GeneratorResult red = staticTile(16, 16, Color.RED);
        GeneratorResult green = staticTile(16, 16, Color.GREEN);
        GeneratorResult blue = staticTile(16, 16, Color.BLUE);
        GeneratorResult yellow = staticTile(16, 16, Color.YELLOW);

        GeneratorResult out = ResultComposer.compose(
                List.of(red, green, blue, yellow),
                CompositeRequest.Layout.GRID,
                0,
                2,
                2);

        BufferedImage img = out.firstFrame();
        // 2 cols x 2 rows of 16x16 => 32x32.
        assertThat(img.getWidth()).isEqualTo(32);
        assertThat(img.getHeight()).isEqualTo(32);
        // row-major: top-left=red, top-right=green, bottom-left=blue, bottom-right=yellow
        assertThat(new Color(img.getRGB(8, 8), true)).isEqualTo(Color.RED);
        assertThat(new Color(img.getRGB(24, 8), true)).isEqualTo(Color.GREEN);
        assertThat(new Color(img.getRGB(8, 24), true)).isEqualTo(Color.BLUE);
        assertThat(new Color(img.getRGB(24, 24), true)).isEqualTo(Color.YELLOW);
    }

    @Test
    void grid_3rows_2cols_dimensions_correct() {
        List<GeneratorResult> inputs = List.of(
                staticTile(16, 16, Color.RED),
                staticTile(16, 16, Color.RED),
                staticTile(16, 16, Color.RED),
                staticTile(16, 16, Color.RED),
                staticTile(16, 16, Color.RED),
                staticTile(16, 16, Color.RED));

        GeneratorResult out = ResultComposer.compose(
                inputs, CompositeRequest.Layout.GRID, 0, 3, 2);

        assertThat(out.firstFrame().getWidth()).isEqualTo(32);  // 2 cols * 16
        assertThat(out.firstFrame().getHeight()).isEqualTo(48); // 3 rows * 16
    }

    @Test
    void grid_with_fewer_children_than_cells_fills_row_major_leaves_empty() {
        GeneratorResult red = staticTile(16, 16, Color.RED);
        GeneratorResult green = staticTile(16, 16, Color.GREEN);
        GeneratorResult blue = staticTile(16, 16, Color.BLUE);

        GeneratorResult out = ResultComposer.compose(
                List.of(red, green, blue),
                CompositeRequest.Layout.GRID, 0, 2, 2);

        BufferedImage img = out.firstFrame();
        // Three cells filled, bottom-right is empty/transparent (alpha == 0).
        assertThat(new Color(img.getRGB(8, 8), true)).isEqualTo(Color.RED);
        assertThat(new Color(img.getRGB(24, 8), true)).isEqualTo(Color.GREEN);
        assertThat(new Color(img.getRGB(8, 24), true)).isEqualTo(Color.BLUE);
        int bottomRight = img.getRGB(24, 24);
        assertThat((bottomRight >>> 24) & 0xFF).isEqualTo(0);
    }

    @Test
    void composite_builder_grid_sets_layout() {
        // Verify CompositeRequest can carry GRID + dims via builder parameters.
        CompositeRequest req = new CompositeRequest(
                List.of(), CompositeRequest.Layout.GRID, 0, 1, 2, 2);
        assertThat(req.layout()).isEqualTo(CompositeRequest.Layout.GRID);
        assertThat(req.gridRows()).isEqualTo(2);
        assertThat(req.gridCols()).isEqualTo(2);
    }

    @Test
    void grid_zero_rows_rejected() {
        assertThatThrownBy(() ->
                new CompositeRequest(
                        List.of(staticItem()),
                        CompositeRequest.Layout.GRID, 0, 1, 0, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gridRows");
    }

    @Test
    void grid_negative_cols_rejected() {
        assertThatThrownBy(() ->
                new CompositeRequest(
                        List.of(staticItem()),
                        CompositeRequest.Layout.GRID, 0, 1, 2, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gridCols");
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static GeneratorResult staticTile(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = net.aerh.tessera.api.image.Graphics2DFactory.createGraphics(img);
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return new GeneratorResult.StaticImage(img);
    }

    private static RenderRequest staticItem() {
        return ItemRequest.builder().itemId("diamond_sword").build();
    }
}
