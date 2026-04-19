package net.aerh.tessera.core.image;

import net.aerh.tessera.api.image.Graphics2DFactory;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the shell contract on {@link Graphics2DFactory}: the 4 mandatory
 * rendering hints are applied on both code paths (factory + retrofit).
 */
class Graphics2DFactoryTest {

    @Test
    void createGraphics_applies_the_four_mandatory_hints() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Graphics2DFactory.createGraphics(img);
        try {
            assertThat(g.getRenderingHint(RenderingHints.KEY_ANTIALIASING))
                    .isEqualTo(RenderingHints.VALUE_ANTIALIAS_OFF);
            assertThat(g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING))
                    .isEqualTo(RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            assertThat(g.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS))
                    .isEqualTo(RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            assertThat(g.getRenderingHint(RenderingHints.KEY_INTERPOLATION))
                    .isEqualTo(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        } finally {
            g.dispose();
        }
    }

    @Test
    void applyDefaultHints_can_retrofit_an_existing_Graphics2D() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            // Deliberately set the opposite hints first to prove applyDefaultHints overrides.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            Graphics2DFactory.applyDefaultHints(g);

            assertThat(g.getRenderingHint(RenderingHints.KEY_ANTIALIASING))
                    .isEqualTo(RenderingHints.VALUE_ANTIALIAS_OFF);
            assertThat(g.getRenderingHint(RenderingHints.KEY_INTERPOLATION))
                    .isEqualTo(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        } finally {
            g.dispose();
        }
    }

    @Test
    void createGraphics_rejects_null_image() {
        assertThatThrownBy(() -> Graphics2DFactory.createGraphics(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("image");
    }

    @Test
    void applyDefaultHints_rejects_null_graphics() {
        assertThatThrownBy(() -> Graphics2DFactory.applyDefaultHints(null))
                .isInstanceOf(NullPointerException.class);
    }
}
