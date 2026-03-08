package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.aerh.imagegenerator.exception.NbtParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnbtParserTest {

    // Basic compound tags

    @Test
    void parseSimpleCompound() {
        JsonObject result = SnbtParser.parse("{id: \"minecraft:diamond_sword\", count: 1}");
        assertThat(result.get("id").getAsString()).isEqualTo("minecraft:diamond_sword");
        assertThat(result.get("count").getAsInt()).isEqualTo(1);
    }

    @Test
    void parseNestedCompound() {
        JsonObject result = SnbtParser.parse("{tag: {display: {Name: \"Cool Sword\"}}}");
        assertThat(result.getAsJsonObject("tag")
            .getAsJsonObject("display")
            .get("Name").getAsString()).isEqualTo("Cool Sword");
    }

    @Test
    void parseEmptyCompound() {
        JsonObject result = SnbtParser.parse("{}");
        assertThat(result.entrySet()).isEmpty();
    }

    // String values

    @Test
    void parseDoubleQuotedString() {
        JsonObject result = SnbtParser.parse("{name: \"hello world\"}");
        assertThat(result.get("name").getAsString()).isEqualTo("hello world");
    }

    @Test
    void parseSingleQuotedString() {
        JsonObject result = SnbtParser.parse("{name: 'hello world'}");
        assertThat(result.get("name").getAsString()).isEqualTo("hello world");
    }

    @Test
    void parseEscapedQuotes() {
        JsonObject result = SnbtParser.parse("{name: \"he said \\\"hi\\\"\"}");
        assertThat(result.get("name").getAsString()).isEqualTo("he said \"hi\"");
    }

    @Test
    void parseUnquotedNamespacedValue() {
        JsonObject result = SnbtParser.parse("{id: minecraft:stone}");
        assertThat(result.get("id").getAsString()).isEqualTo("minecraft:stone");
    }

    @Test
    void parseQuotedNamespacedValue() {
        JsonObject result = SnbtParser.parse("{id: \"minecraft:stone\"}");
        assertThat(result.get("id").getAsString()).isEqualTo("minecraft:stone");
    }

    @Test
    void parseUnquotedNamespacedValueInList() {
        JsonObject result = SnbtParser.parse("{items: [minecraft:stone, minecraft:dirt]}");
        JsonArray array = result.getAsJsonArray("items");
        assertThat(array).hasSize(2);
        assertThat(array.get(0).getAsString()).isEqualTo("minecraft:stone");
        assertThat(array.get(1).getAsString()).isEqualTo("minecraft:dirt");
    }

    // Numeric types

    @Test
    void parseByte() {
        JsonObject result = SnbtParser.parse("{val: 1b}");
        assertThat(result.get("val").getAsInt()).isEqualTo(1);
    }

    @Test
    void parseShort() {
        JsonObject result = SnbtParser.parse("{val: 100s}");
        assertThat(result.get("val").getAsInt()).isEqualTo(100);
    }

    @Test
    void parseInt() {
        JsonObject result = SnbtParser.parse("{val: 42}");
        assertThat(result.get("val").getAsInt()).isEqualTo(42);
    }

    @Test
    void parseLong() {
        JsonObject result = SnbtParser.parse("{val: 100L}");
        assertThat(result.get("val").getAsLong()).isEqualTo(100L);
    }

    @Test
    void parseFloat() {
        JsonObject result = SnbtParser.parse("{val: 1.5f}");
        assertThat(result.get("val").getAsFloat()).isEqualTo(1.5f);
    }

    @Test
    void parseDouble() {
        JsonObject result = SnbtParser.parse("{val: 1.5d}");
        assertThat(result.get("val").getAsDouble()).isEqualTo(1.5);
    }

    @Test
    void parseUntypedDouble() {
        JsonObject result = SnbtParser.parse("{val: 1.5}");
        assertThat(result.get("val").getAsDouble()).isEqualTo(1.5);
    }

    @Test
    void parseNegativeNumber() {
        JsonObject result = SnbtParser.parse("{val: -42}");
        assertThat(result.get("val").getAsInt()).isEqualTo(-42);
    }

    // Boolean values

    @Test
    void parseTrueAsString() {
        JsonObject result = SnbtParser.parse("{val: true}");
        // Parser stores boolean-like values as strings, not bytes
        assertThat(result.get("val").getAsString()).isEqualTo("true");
    }

    @Test
    void parseFalseAsString() {
        JsonObject result = SnbtParser.parse("{val: false}");
        assertThat(result.get("val").getAsString()).isEqualTo("false");
    }

    // Lists

    @Test
    void parseSimpleList() {
        JsonObject result = SnbtParser.parse("{items: [\"a\", \"b\", \"c\"]}");
        JsonArray array = result.getAsJsonArray("items");
        assertThat(array).hasSize(3);
        assertThat(array.get(0).getAsString()).isEqualTo("a");
        assertThat(array.get(2).getAsString()).isEqualTo("c");
    }

    @Test
    void parseEmptyList() {
        JsonObject result = SnbtParser.parse("{items: []}");
        assertThat(result.getAsJsonArray("items")).isEmpty();
    }

    @Test
    void parseIndexedList() {
        JsonObject result = SnbtParser.parse("{items: [0: \"first\", 1: \"second\"]}");
        JsonArray array = result.getAsJsonArray("items");
        assertThat(array).hasSize(2);
        assertThat(array.get(0).getAsString()).isEqualTo("first");
        assertThat(array.get(1).getAsString()).isEqualTo("second");
    }

    // Typed arrays

    @Test
    void parseByteArray() {
        JsonObject result = SnbtParser.parse("{data: [B; 1b, 2b, 3b]}");
        JsonArray array = result.getAsJsonArray("data");
        assertThat(array).hasSize(3);
        assertThat(array.get(0).getAsInt()).isEqualTo(1);
    }

    @Test
    void parseIntArray() {
        JsonObject result = SnbtParser.parse("{data: [I; 1, 2, 3]}");
        JsonArray array = result.getAsJsonArray("data");
        assertThat(array).hasSize(3);
    }

    @Test
    void parseLongArray() {
        JsonObject result = SnbtParser.parse("{data: [L; 1L, 2L]}");
        JsonArray array = result.getAsJsonArray("data");
        assertThat(array).hasSize(2);
    }

    // Quoted keys

    @Test
    void parseQuotedKey() {
        JsonObject result = SnbtParser.parse("{\"my key\": \"value\"}");
        assertThat(result.get("my key").getAsString()).isEqualTo("value");
    }

    // Real-world NBT structures

    @Test
    void parseMinecraftItemNbt() {
        String snbt = "{id: \"minecraft:diamond_sword\", count: 1, tag: {display: {Name: '{\"text\":\"Cool Sword\",\"color\":\"gold\"}', Lore: ['{\"text\":\"A legendary blade\",\"color\":\"gray\"}']}}}";
        JsonObject result = SnbtParser.parse(snbt);

        assertThat(result.get("id").getAsString()).isEqualTo("minecraft:diamond_sword");
        assertThat(result.get("count").getAsInt()).isEqualTo(1);

        JsonObject display = result.getAsJsonObject("tag").getAsJsonObject("display");
        assertThat(display.get("Name").getAsString()).contains("Cool Sword");

        JsonArray lore = display.getAsJsonArray("Lore");
        assertThat(lore).hasSize(1);
    }

    @Test
    void parseComponentsFormatNbt() {
        String snbt = "{id: \"minecraft:diamond_sword\", components: {\"minecraft:custom_name\": '{\"text\":\"My Sword\"}'}}";
        JsonObject result = SnbtParser.parse(snbt);

        assertThat(result.has("components")).isTrue();
        JsonObject components = result.getAsJsonObject("components");
        assertThat(components.has("minecraft:custom_name")).isTrue();
    }

    // Error cases

    @Test
    void nullInputThrows() {
        assertThatThrownBy(() -> SnbtParser.parse(null))
            .isInstanceOf(NbtParseException.class);
    }

    @Test
    void blankInputThrows() {
        assertThatThrownBy(() -> SnbtParser.parse("   "))
            .isInstanceOf(NbtParseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not snbt at all", "just a string"})
    void invalidInputThrows(String input) {
        assertThatThrownBy(() -> SnbtParser.parse(input))
            .isInstanceOf(NbtParseException.class);
    }

    // Whitespace handling

    @Test
    void handlesExtraWhitespace() {
        JsonObject result = SnbtParser.parse("{  id :  \"stone\" ,  count :  1  }");
        assertThat(result.get("id").getAsString()).isEqualTo("stone");
        assertThat(result.get("count").getAsInt()).isEqualTo(1);
    }
}
