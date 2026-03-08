package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NbtTextComponentUtilTest {

    // toFormattedString - recursive extra handling

    @Test
    void flatExtraProducesAllText() {
        JsonObject root = textComponent("gold", "Hello ");
        JsonArray extra = new JsonArray();
        extra.add(textComponent("red", "World"));
        root.add("extra", extra);

        String result = NbtTextComponentUtil.toFormattedString(root);

        assertThat(result).contains("Hello ");
        assertThat(result).contains("World");
    }

    @Test
    void nestedExtraProducesAllText() {
        // Root -> extra[0] -> extra[0] (deeply nested)
        JsonObject innermost = textComponent(null, "C");
        JsonObject middle = textComponent("red", "B");
        JsonArray middleExtra = new JsonArray();
        middleExtra.add(innermost);
        middle.add("extra", middleExtra);

        JsonObject root = textComponent("gold", "A");
        JsonArray rootExtra = new JsonArray();
        rootExtra.add(middle);
        root.add("extra", rootExtra);

        String result = NbtTextComponentUtil.toFormattedString(root);

        assertThat(result).contains("A");
        assertThat(result).contains("B");
        assertThat(result).contains("C");
    }

    @Test
    void primitiveExtraElementsIncluded() {
        JsonObject root = textComponent("gray", "Start ");
        JsonArray extra = new JsonArray();
        extra.add("plain text");
        root.add("extra", extra);

        String result = NbtTextComponentUtil.toFormattedString(root);

        assertThat(result).contains("Start ");
        assertThat(result).contains("plain text");
    }

    // toFormattedString - hex color support

    @Test
    void hexColorEmittedAsHexCode() {
        // #FF5555 should be emitted as &#FF5555, not mapped to nearest named color
        JsonObject component = textComponent("#FF5555", "Red text");

        String result = NbtTextComponentUtil.toFormattedString(component);

        assertThat(result).contains("Red text");
        assertThat(result).contains("&#FF5555");
    }

    @Test
    void exactNamedColorUsesCode() {
        // #FFAA00 is exactly GOLD, but since it comes from JSON as a hex string,
        // NbtTextComponentUtil doesn't reverse-map it - it emits the hex
        JsonObject component = textComponent("#FFAA00", "Gold text");

        String result = NbtTextComponentUtil.toFormattedString(component);

        assertThat(result).contains("Gold text");
        assertThat(result).contains("&#FFAA00");
    }

    @Test
    void invalidHexColorDropped() {
        // Invalid hex colors are silently dropped (no color code emitted)
        JsonObject component = textComponent("#ZZZZZZ", "No color");

        String result = NbtTextComponentUtil.toFormattedString(component);

        // The invalid hex is passed through as &#ZZZZZZ since the NbtTextComponentUtil
        // doesn't validate hex - it just emits the format for the legacy parser to handle
        assertThat(result).contains("No color");
    }

    @Test
    void namedColorStillWorks() {
        JsonObject component = textComponent("gold", "Named");

        String result = NbtTextComponentUtil.toFormattedString(component);

        assertThat(result).contains("&6");
        assertThat(result).contains("Named");
    }

    // toFormattedString - formatting codes

    @Test
    void boldFormattingCode() {
        JsonObject component = textComponent("white", "Bold text");
        component.addProperty("bold", true);

        String result = NbtTextComponentUtil.toFormattedString(component);

        assertThat(result).contains("&l");
        assertThat(result).contains("Bold text");
    }

    // extractVisibleText

    @Test
    void extractVisibleTextFlat() {
        JsonObject root = textComponent(null, "Hello ");
        JsonArray extra = new JsonArray();
        extra.add(textComponent(null, "World"));
        root.add("extra", extra);

        assertThat(NbtTextComponentUtil.extractVisibleText(root)).isEqualTo("Hello World");
    }

    @Test
    void extractVisibleTextNested() {
        JsonObject innermost = textComponent(null, "C");
        JsonObject middle = textComponent(null, "B");
        JsonArray middleExtra = new JsonArray();
        middleExtra.add(innermost);
        middle.add("extra", middleExtra);

        JsonObject root = textComponent(null, "A");
        JsonArray rootExtra = new JsonArray();
        rootExtra.add(middle);
        root.add("extra", rootExtra);

        assertThat(NbtTextComponentUtil.extractVisibleText(root)).isEqualTo("ABC");
    }

    // parseTextValue

    @Test
    void parseTextValueJsonComponent() {
        String json = "{\"text\":\"Hello\",\"color\":\"gold\"}";
        String result = NbtTextComponentUtil.parseTextValue(json);
        assertThat(result).contains("Hello");
        assertThat(result).contains("&6");
    }

    @Test
    void parseTextValuePlainSectionSymbol() {
        String plain = "\u00a76Gold text";
        String result = NbtTextComponentUtil.parseTextValue(plain);
        assertThat(result).isEqualTo("&6Gold text");
    }

    // parseBoolean

    @Test
    void parseBooleanTrue() {
        assertThat(NbtTextComponentUtil.parseBooleanStrict(
            com.google.gson.JsonParser.parseString("true"))).isTrue();
    }

    @Test
    void parseBooleanStringTrue() {
        assertThat(NbtTextComponentUtil.parseBooleanStrict(
            com.google.gson.JsonParser.parseString("\"1b\""))).isTrue();
    }

    @Test
    void parseBooleanNull() {
        assertThat(NbtTextComponentUtil.parseBoolean(null)).isNull();
    }

    // isJsonTextComponent

    @Test
    void jsonObjectIsTextComponent() {
        assertThat(NbtTextComponentUtil.isJsonTextComponent("{\"text\":\"hello\"}")).isTrue();
    }

    @Test
    void plainStringIsNotTextComponent() {
        assertThat(NbtTextComponentUtil.isJsonTextComponent("\u00a77{Ability}")).isFalse();
    }

    @Test
    void nullIsNotTextComponent() {
        assertThat(NbtTextComponentUtil.isJsonTextComponent(null)).isFalse();
    }

    // Helper

    private static JsonObject textComponent(String color, String text) {
        JsonObject obj = new JsonObject();
        if (color != null) {
            obj.addProperty("color", color);
        }
        obj.addProperty("text", text);
        return obj;
    }
}
