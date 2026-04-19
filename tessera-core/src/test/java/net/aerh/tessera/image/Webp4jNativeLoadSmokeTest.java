package net.aerh.tessera.image;

import dev.matrixlab.webp4j.WebPCodec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Spike A1 from 01-RESEARCH.md §Assumptions Log: confirms the webp4j 2.1.1 JNI native binary
 * autoloads on this developer's platform (Windows/macOS/Linux) from an empty {@code ~/.m2}.
 *
 * <p>Tagged {@code @Tag("spike")} so it is excluded from default CI runs by consumers who set
 * a tag filter; here it runs as part of the normal suite (Surefire has no tag exclusions
 * configured). If {@link UnsatisfiedLinkError} is thrown, consult 01-RESEARCH.md the relevant research section.
 */
@Tag("spike")
class Webp4jNativeLoadSmokeTest {

    @Test
    void webp4jJniLoadsAndEncodesTinyImage() {
        BufferedImage tiny = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        assertThatCode(() -> {
            byte[] out = WebPCodec.encodeLosslessImage(tiny);
            assertThat(out).isNotEmpty();
            assertThat(out[0]).isEqualTo((byte) 'R'); // RIFF magic
        }).doesNotThrowAnyException();
    }
}
