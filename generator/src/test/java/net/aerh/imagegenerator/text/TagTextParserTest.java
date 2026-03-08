package net.aerh.imagegenerator.text;

import net.aerh.imagegenerator.text.segment.ColorSegment;
import net.aerh.imagegenerator.text.segment.LineSegment;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TagTextParserTest {

    // Named colors

    @Test
    void parseNamedColor() {
        LineSegment line = TagTextParser.parseLine("<red>Hello");
        ColorSegment seg = line.getSegments().get(0);
        assertThat(seg.getText()).isEqualTo("Hello");
        assertThat(seg.getForegroundColor()).isEqualTo(ChatFormat.RED.getColor());
    }

    @Test
    void parseNamedColorWithClosingTag() {
        LineSegment line = TagTextParser.parseLine("<gold>Gold</gold> Normal");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs.get(0).getText()).isEqualTo("Gold");
        assertThat(segs.get(0).getForegroundColor()).isEqualTo(ChatFormat.GOLD.getColor());
        // After closing, color resets to default
        assertThat(segs.get(1).getText()).isEqualTo(" Normal");
    }

    // Hex colors

    @Test
    void parseHexColor() {
        LineSegment line = TagTextParser.parseLine("<#FF5555>Red text");
        ColorSegment seg = line.getSegments().get(0);
        assertThat(seg.getText()).isEqualTo("Red text");
        assertThat(seg.getForegroundColor()).isEqualTo(new Color(0xFF5555));
    }

    @Test
    void parseColorTagWithHex() {
        LineSegment line = TagTextParser.parseLine("<color:#00FF00>Green");
        ColorSegment seg = line.getSegments().get(0);
        assertThat(seg.getText()).isEqualTo("Green");
        assertThat(seg.getForegroundColor()).isEqualTo(new Color(0x00FF00));
    }

    @Test
    void parseColorTagWithName() {
        LineSegment line = TagTextParser.parseLine("<color:gold>Gold");
        ColorSegment seg = line.getSegments().get(0);
        assertThat(seg.getText()).isEqualTo("Gold");
        assertThat(seg.getForegroundColor()).isEqualTo(ChatFormat.GOLD.getColor());
    }

    // Decorations

    @Test
    void parseBold() {
        LineSegment line = TagTextParser.parseLine("<bold>Bold text</bold> normal");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs.get(0).getText()).isEqualTo("Bold text");
        assertThat(segs.get(0).isBold()).isTrue();
        assertThat(segs.get(1).getText()).isEqualTo(" normal");
        assertThat(segs.get(1).isBold()).isFalse();
    }

    @Test
    void parseBoldShorthand() {
        LineSegment line = TagTextParser.parseLine("<b>Bold</b>");
        assertThat(line.getSegments().get(0).isBold()).isTrue();
    }

    @Test
    void parseItalicShorthands() {
        assertThat(TagTextParser.parseLine("<i>text</i>").getSegments().get(0).isItalic()).isTrue();
        assertThat(TagTextParser.parseLine("<em>text</em>").getSegments().get(0).isItalic()).isTrue();
        assertThat(TagTextParser.parseLine("<italic>text</italic>").getSegments().get(0).isItalic()).isTrue();
    }

    @Test
    void parseUnderlined() {
        LineSegment line = TagTextParser.parseLine("<u>underlined</u>");
        assertThat(line.getSegments().get(0).isUnderlined()).isTrue();
    }

    @Test
    void parseStrikethrough() {
        LineSegment line = TagTextParser.parseLine("<st>struck</st>");
        assertThat(line.getSegments().get(0).isStrikethrough()).isTrue();
    }

    @Test
    void parseObfuscated() {
        LineSegment line = TagTextParser.parseLine("<obf>magic</obf>");
        assertThat(line.getSegments().get(0).isObfuscated()).isTrue();
    }

    // Combined color + decoration

    @Test
    void parseColorAndBold() {
        LineSegment line = TagTextParser.parseLine("<red><bold>Red Bold</bold></red>");
        ColorSegment seg = line.getSegments().get(0);
        assertThat(seg.getText()).isEqualTo("Red Bold");
        assertThat(seg.getForegroundColor()).isEqualTo(ChatFormat.RED.getColor());
        assertThat(seg.isBold()).isTrue();
    }

    // Gradient

    @Test
    void parseGradient() {
        LineSegment line = TagTextParser.parseLine("<gradient:red:blue>Hello</gradient>");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(5); // one per character

        // First char should be red-ish
        assertThat(segs.get(0).getText()).isEqualTo("H");
        assertThat(segs.get(0).getForegroundColor().getRed()).isGreaterThan(200);

        // Last char should be blue-ish
        assertThat(segs.get(4).getText()).isEqualTo("o");
        assertThat(segs.get(4).getForegroundColor().getBlue()).isGreaterThan(200);
    }

    @Test
    void parseGradientWithHexColors() {
        LineSegment line = TagTextParser.parseLine("<gradient:#FF0000:#0000FF>AB</gradient>");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(2);
        assertThat(segs.get(0).getForegroundColor()).isEqualTo(new Color(0xFF0000));
        assertThat(segs.get(1).getForegroundColor()).isEqualTo(new Color(0x0000FF));
    }

    @Test
    void parseMultiStopGradient() {
        LineSegment line = TagTextParser.parseLine("<gradient:red:green:blue>ABC</gradient>");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(3);
        // Middle should be green-ish
        assertThat(segs.get(1).getForegroundColor().getGreen()).isGreaterThan(200);
    }

    // Rainbow

    @Test
    void parseRainbow() {
        LineSegment line = TagTextParser.parseLine("<rainbow>Hello!</rainbow>");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(6); // one per character

        // Each character should have a different color
        for (int i = 0; i < segs.size() - 1; i++) {
            assertThat(segs.get(i).getForegroundColor())
                .isNotEqualTo(segs.get(i + 1).getForegroundColor());
        }
    }

    @Test
    void parseRainbowWithPhase() {
        LineSegment line = TagTextParser.parseLine("<rainbow:0.5>AB</rainbow>");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs).hasSize(2);

        // Colors should be different from zero-phase rainbow
        LineSegment noPhase = TagTextParser.parseLine("<rainbow>AB</rainbow>");
        assertThat(segs.get(0).getForegroundColor())
            .isNotEqualTo(noPhase.getSegments().get(0).getForegroundColor());
    }

    // Reset

    @Test
    void parseReset() {
        LineSegment line = TagTextParser.parseLine("<red><bold>styled<reset>plain");
        List<ColorSegment> segs = line.getSegments();
        assertThat(segs.get(0).isBold()).isTrue();
        assertThat(segs.get(1).isBold()).isFalse();
    }

    // Newlines (tested via LineSegment.fromTagFormat which handles splitting)

    @Test
    void parseNewlines() {
        List<LineSegment> lines = LineSegment.fromTagFormat("Line 1\nLine 2");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getSegments().get(0).getText()).isEqualTo("Line 1");
        assertThat(lines.get(1).getSegments().get(0).getText()).isEqualTo("Line 2");
    }

    @Test
    void parseEscapedNewlines() {
        List<LineSegment> lines = LineSegment.fromTagFormat("Line 1\\nLine 2");
        assertThat(lines).hasSize(2);
    }

    // Edge cases

    @Test
    void plainTextNoTags() {
        LineSegment line = TagTextParser.parseLine("Just plain text");
        assertThat(line.getSegments().get(0).getText()).isEqualTo("Just plain text");
    }

    @Test
    void unrecognizedTagTreatedAsLiteral() {
        LineSegment line = TagTextParser.parseLine("<unknown>text");
        // Unrecognized tag is included as literal text
        String fullText = line.getSegments().stream()
            .map(ColorSegment::getText)
            .reduce("", String::concat);
        assertThat(fullText).contains("<unknown>");
        assertThat(fullText).contains("text");
    }

    @Test
    void unclosedAngleBracketTreatedAsLiteral() {
        LineSegment line = TagTextParser.parseLine("5 < 10 is true");
        String fullText = line.getSegments().stream()
            .map(ColorSegment::getText)
            .reduce("", String::concat);
        assertThat(fullText).contains("5 < 10 is true");
    }

    @Test
    void emptyInput() {
        LineSegment line = TagTextParser.parseLine("");
        assertThat(line.getSegments()).hasSize(1);
    }

    // Detection

    @Test
    void containsTagsDetection() {
        assertThat(TagTextParser.containsTags("<red>hello")).isTrue();
        assertThat(TagTextParser.containsTags("no tags here")).isFalse();
        assertThat(TagTextParser.containsTags("&6legacy")).isFalse();
    }

    // Shadow color computation

    @Test
    void hexColorHasComputedShadow() {
        LineSegment line = TagTextParser.parseLine("<#FF0000>Red");
        ColorSegment seg = line.getSegments().get(0);
        // Shadow = RGB / 4
        assertThat(seg.getShadowColor()).isEqualTo(new Color(63, 0, 0));
    }

    // Gradient color computation (unit tests)

    @Test
    void gradientTwoColors() {
        Color[] colors = TagTextParser.computeGradientColors(3, new String[]{"gradient", "#FF0000", "#0000FF"});
        assertThat(colors).hasSize(3);
        assertThat(colors[0]).isEqualTo(new Color(255, 0, 0));
        assertThat(colors[2]).isEqualTo(new Color(0, 0, 255));
        // Middle should be purple-ish
        assertThat(colors[1].getRed()).isGreaterThan(100);
        assertThat(colors[1].getBlue()).isGreaterThan(100);
    }

    @Test
    void rainbowCoversSpectrum() {
        Color[] colors = TagTextParser.computeRainbowColors(6, 0f);
        assertThat(colors).hasSize(6);
        // First color should be red (hue 0)
        assertThat(colors[0].getRed()).isEqualTo(255);
    }
}
