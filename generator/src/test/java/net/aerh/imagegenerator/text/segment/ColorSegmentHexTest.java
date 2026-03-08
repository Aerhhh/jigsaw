package net.aerh.imagegenerator.text.segment;

import net.aerh.imagegenerator.text.ChatFormat;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColorSegmentHexTest {

    // Legacy hex format: &#RRGGBB

    @Test
    void parseLegacyHexColor() {
        LineSegment line = ColorSegment.fromLegacy("&#FF5555Red text");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getText()).isEqualTo("Red text");
        assertThat(segs.get(0).getForegroundColor()).isEqualTo(new Color(0xFF5555));
    }

    @Test
    void parseLegacyHexWithNamedColor() {
        LineSegment line = ColorSegment.fromLegacy("&6Gold &#00FF00Green");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs.get(0).getText()).isEqualTo("Gold ");
        assertThat(segs.get(0).getForegroundColor()).isEqualTo(ChatFormat.GOLD.getColor());
        assertThat(segs.get(1).getText()).isEqualTo("Green");
        assertThat(segs.get(1).getForegroundColor()).isEqualTo(new Color(0x00FF00));
    }

    // BungeeCord hex format: &x&R&R&G&G&B&B

    @Test
    void parseBungeeHexColor() {
        // &x&F&F&5&5&5&5 = #FF5555
        LineSegment line = ColorSegment.fromLegacy("&x&F&F&5&5&5&5Red text");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getText()).isEqualTo("Red text");
        assertThat(segs.get(0).getForegroundColor()).isEqualTo(new Color(0xFF5555));
    }

    @Test
    void parseBungeeHexWithSectionSymbol() {
        // Same format but with section symbols
        LineSegment line = ColorSegment.fromLegacy("\u00a7x\u00a7F\u00a7F\u00a75\u00a75\u00a75\u00a75Red text");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getForegroundColor()).isEqualTo(new Color(0xFF5555));
    }

    // Shadow color computation

    @Test
    void hexColorShadowComputed() {
        LineSegment line = ColorSegment.fromLegacy("&#FF0000Red");
        ColorSegment seg = line.getSegments().get(0);
        // Shadow = RGB / 4 -> (63, 0, 0)
        assertThat(seg.getShadowColor()).isEqualTo(new Color(63, 0, 0));
    }

    @Test
    void namedColorShadowFromChatFormat() {
        LineSegment line = ColorSegment.fromLegacy("&6Gold");
        ColorSegment seg = line.getSegments().get(0);
        assertThat(seg.getShadowColor()).isEqualTo(ChatFormat.GOLD.getBackgroundColor());
    }

    // Color storage

    @Test
    void setColorFromChatFormat() {
        ColorSegment seg = new ColorSegment("test");
        seg.setColor(ChatFormat.RED);
        assertThat(seg.getForegroundColor()).isEqualTo(ChatFormat.RED.getColor());
        assertThat(seg.getShadowColor()).isEqualTo(ChatFormat.RED.getBackgroundColor());
    }

    @Test
    void setColorFromAwtColor() {
        ColorSegment seg = new ColorSegment("test");
        seg.setColor(new Color(0x123456));
        assertThat(seg.getForegroundColor()).isEqualTo(new Color(0x123456));
        assertThat(seg.getShadowColor()).isEqualTo(new Color(0x04, 0x0D, 0x15));
    }

    // JSON serialization

    @Test
    void toJsonWithNamedColor() {
        ColorSegment seg = new ColorSegment("test");
        seg.setColor(ChatFormat.GOLD);
        assertThat(seg.toJson().get("color").getAsString()).isEqualTo("gold");
    }

    @Test
    void toJsonWithHexColor() {
        ColorSegment seg = new ColorSegment("test");
        seg.setColor(new Color(0xAB1234)); // not a named MC color
        assertThat(seg.toJson().get("color").getAsString()).isEqualTo("#AB1234");
    }

    // Legacy serialization

    @Test
    void toLegacyWithNamedColor() {
        ColorSegment seg = new ColorSegment("Gold");
        seg.setColor(ChatFormat.GOLD);
        String legacy = seg.toLegacy('&');
        assertThat(legacy).contains("&6");
        assertThat(legacy).contains("Gold");
    }

    @Test
    void toLegacyWithHexColor() {
        ColorSegment seg = new ColorSegment("Custom");
        seg.setColor(new Color(0xAB1234)); // not a named MC color
        String legacy = seg.toLegacy('&');
        // Should use BungeeCord format: &x&A&B&1&2&3&4
        assertThat(legacy).contains("&x&A&B&1&2&3&4");
        assertThat(legacy).contains("Custom");
    }

    // Edge cases

    @Test
    void invalidHexIgnored() {
        // &#ZZZZZZ should not be parsed as a hex color
        LineSegment line = ColorSegment.fromLegacy("&#ZZZZZZtext");
        // The & is consumed but # doesn't start a valid hex, so it's literal
        String fullText = line.getSegments().stream()
            .map(ColorSegment::getText)
            .reduce("", String::concat);
        assertThat(fullText).contains("text");
    }

    @Test
    void hexColorFollowedByFormatting() {
        LineSegment line = ColorSegment.fromLegacy("&#FF5555&lBold Red");
        List<ColorSegment> segs = line.getSegments();
        // Should have hex color and bold
        boolean foundBoldRed = segs.stream().anyMatch(s ->
            s.isBold() && s.getForegroundColor().equals(new Color(0xFF5555)));
        assertThat(foundBoldRed).isTrue();
    }
}
