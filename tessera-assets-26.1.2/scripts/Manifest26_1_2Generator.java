// Single-file Java program (JEP 330) to regenerate the Tessera manifest for MC 26.1.2.
// Run with: java tessera-assets-26.1.2/scripts/Manifest26_1_2Generator.java
//
// Why a single-file source program rather than a tooling Maven module:
// - The tooling/ module was intentionally removed from the reactor (only target/
//   remained from a deleted source tree); re-introducing it just for this one regen would
//   undo that decision.
// - This is a one-shot regen, not a recurring build step. A scripts/ directory + JEP 330
//   source-mode keeps the regen reproducible (anyone with JDK 21 can run it) without
//   committing build infrastructure.
// - The output (manifest.json) IS the contract; the script is documentation of how it was
//   produced.
//
// This program:
// 1. Fetches piston-meta version_manifest_v2.json over HTTPS.
// 2. Locates the 26.1.2 entry and follows its per-version manifest URL.
// 3. Dereferences the assetIndex URL and parses its objects map.
// 4. Selects the same 11 paths the earlier 1.21.4 manifest pinned (icons + unifont + 4 langs).
// 5. Writes a Tessera manifest JSON to
//    tessera-assets-26.1.2/src/main/resources/tessera/assets/26.1.2/manifest.json
//    in the schema established by AssetManifest + AssetEntry.
//
// Uses java.net.http.HttpClient + JEP 330 single-file source mode; only stdlib.
// Hand-rolled JSON parsing (no external deps) for portability.

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Manifest26_1_2Generator {

    private static final String VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String TARGET_MC_VERSION = "26.1.2";
    private static final Path OUTPUT_PATH = Path.of(
            "tessera-assets-26.1.2/src/main/resources/tessera/assets/26.1.2/manifest.json");

    /**
     * The same 11 asset paths the earlier 1.21.4 manifest pinned. Skipped if absent in 26.1.2's
     * assetIndex (logged as WARNING; downstream `Manifest26_1_2Test` requires at least 5).
     */
    private static final List<String> SELECTED_PATHS = List.of(
            "icons/icon_16x16.png",
            "icons/icon_32x32.png",
            "icons/icon_48x48.png",
            "icons/icon_128x128.png",
            "icons/icon_256x256.png",
            "minecraft/font/include/unifont.json",
            "minecraft/font/unifont.zip",
            "minecraft/lang/en_gb.json",
            "minecraft/lang/de_de.json",
            "minecraft/lang/fr_fr.json",
            "minecraft/lang/ja_jp.json"
    );

    public static void main(String[] args) throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // 1. Top-level version manifest -> 26.1.2 url.
        Map<String, Object> versionManifest = JsonParser.parseObject(get(http, VERSION_MANIFEST_URL));
        @SuppressWarnings("unchecked")
        List<Object> versions = (List<Object>) versionManifest.get("versions");
        String verUrl = null;
        for (Object v : versions) {
            @SuppressWarnings("unchecked")
            Map<String, Object> vo = (Map<String, Object>) v;
            if (TARGET_MC_VERSION.equals(vo.get("id"))) {
                verUrl = (String) vo.get("url");
                break;
            }
        }
        if (verUrl == null) {
            throw new IllegalStateException(
                    TARGET_MC_VERSION + " not found in version_manifest_v2; piston-meta may have moved");
        }

        // 2. Per-version manifest -> assetIndex url.
        Map<String, Object> verManifest = JsonParser.parseObject(get(http, verUrl));
        @SuppressWarnings("unchecked")
        Map<String, Object> assetIndexMeta = (Map<String, Object>) verManifest.get("assetIndex");
        String assetIndexUrl = (String) assetIndexMeta.get("url");

        // 3. Asset index objects map.
        Map<String, Object> assetIndex = JsonParser.parseObject(get(http, assetIndexUrl));
        @SuppressWarnings("unchecked")
        Map<String, Object> objects = (Map<String, Object>) assetIndex.get("objects");

        // 4. Build manifest entries.
        List<Map<String, Object>> files = new ArrayList<>();
        for (String path : SELECTED_PATHS) {
            Object entryObj = objects.get(path);
            if (entryObj == null) {
                System.err.println("WARNING: " + path + " absent in " + TARGET_MC_VERSION
                        + " assetIndex; skipping");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) entryObj;
            String sha1 = (String) entry.get("hash");
            long size = ((Number) entry.get("size")).longValue();
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", path);
            file.put("sha1", sha1);
            file.put("size", size);
            file.put("url", "https://resources.download.minecraft.net/" + sha1.substring(0, 2) + "/" + sha1);
            files.add(file);
        }

        // 5. Serialize + write.
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", TARGET_MC_VERSION);
        manifest.put("files", files);

        Files.createDirectories(OUTPUT_PATH.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(OUTPUT_PATH, StandardCharsets.UTF_8)) {
            w.write(JsonWriter.write(manifest, 0));
            w.newLine();
        }
        System.out.println("Wrote " + OUTPUT_PATH + " with " + files.size() + " files");
    }

    private static String get(HttpClient http, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("GET " + url + " returned HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    // ---- Minimal JSON parser (sufficient for piston-meta's well-formed output) ----------

    static final class JsonParser {
        private final String s;
        private int i;

        private JsonParser(String s) {
            this.s = s;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> parseObject(String json) {
            JsonParser p = new JsonParser(json);
            p.skipWhitespace();
            return (Map<String, Object>) p.parseValue();
        }

        private Object parseValue() {
            skipWhitespace();
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObj();
                case '[' -> parseArr();
                case '"' -> parseString();
                case 't', 'f' -> parseBool();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObj() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                } else if (c == '}') {
                    i++;
                    return map;
                } else {
                    throw new IllegalStateException("expected , or } at " + i + " got " + c);
                }
            }
        }

        private List<Object> parseArr() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                } else if (c == ']') {
                    i++;
                    return list;
                } else {
                    throw new IllegalStateException("expected , or ] at " + i + " got " + c);
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalStateException("bad escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object parseNumber() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.'
                    || s.charAt(i) == 'e' || s.charAt(i) == 'E' || s.charAt(i) == '+' || s.charAt(i) == '-')) {
                i++;
            }
            String num = s.substring(start, i);
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                return Double.parseDouble(num);
            }
            return Long.parseLong(num);
        }

        private Boolean parseBool() {
            if (s.startsWith("true", i)) {
                i += 4;
                return true;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return false;
            }
            throw new IllegalStateException("bad bool at " + i);
        }

        private Object parseNull() {
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw new IllegalStateException("bad null at " + i);
        }

        private void expect(char c) {
            if (s.charAt(i) != c) {
                throw new IllegalStateException("expected " + c + " at " + i + " got " + s.charAt(i));
            }
            i++;
        }

        private char peek() {
            return s.charAt(i);
        }

        private void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }

    // ---- Minimal JSON writer (pretty-printed, 2-space indent) ----------------------------

    static final class JsonWriter {

        @SuppressWarnings("unchecked")
        static String write(Object value, int indent) {
            if (value == null) return "null";
            if (value instanceof Boolean b) return Boolean.toString(b);
            if (value instanceof Long l) return Long.toString(l);
            if (value instanceof Integer n) return Integer.toString(n);
            if (value instanceof Double d) return Double.toString(d);
            if (value instanceof String s) return quote(s);
            if (value instanceof Map<?, ?> map) return writeObject((Map<String, Object>) map, indent);
            if (value instanceof List<?> list) return writeArray((List<Object>) list, indent);
            throw new IllegalArgumentException("cannot serialize " + value.getClass());
        }

        private static String writeObject(Map<String, Object> map, int indent) {
            if (map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            int n = map.size();
            int idx = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                sb.append("  ".repeat(indent + 1));
                sb.append(quote(e.getKey())).append(": ");
                sb.append(write(e.getValue(), indent + 1));
                if (++idx < n) sb.append(',');
                sb.append('\n');
            }
            sb.append("  ".repeat(indent)).append('}');
            return sb.toString();
        }

        private static String writeArray(List<Object> list, int indent) {
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            int n = list.size();
            int idx = 0;
            for (Object v : list) {
                sb.append("  ".repeat(indent + 1));
                sb.append(write(v, indent + 1));
                if (++idx < n) sb.append(',');
                sb.append('\n');
            }
            sb.append("  ".repeat(indent)).append(']');
            return sb.toString();
        }

        private static String quote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (int j = 0; j < s.length(); j++) {
                char c = s.charAt(j);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
            return sb.toString();
        }
    }
}
