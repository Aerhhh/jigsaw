package net.aerh.tessera.core.generator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utility for loading Minecraft skin images from Base64-encoded profile texture
 * properties, direct URLs, or Mojang usernames.
 *
 * <p>Used by both {@link PlayerHeadGenerator} and
 * {@link PlayerBodyGenerator} to avoid duplicating the skin-loading logic.  added
 * the third {@link #loadByUsername(String)} entry-point on top of a Caffeine cache that
 * is dedupe-keyed by lowercased username (TTL 24h, maxSize 1024). The caffeine
 * {@link Ticker} is injectable via the package-private ctor so tests can advance virtual
 * time for TTL expiry assertions without sleeping.
 */
public final class SkinLoader {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long SKIN_CACHE_MAX_SIZE = 256;
    private static final Duration SKIN_CACHE_TTL = Duration.ofMinutes(10);

    private static final long USERNAME_CACHE_MAX_SIZE = 1024;
    private static final Duration USERNAME_CACHE_TTL = Duration.ofHours(24);

    private final HttpClient httpClient;
    private final Cache<String, BufferedImage> skinCache;
    private final Cache<String, BufferedImage> skinByUsernameCache;

    /**
     * Creates a new skin loader with the given HTTP client and the system ticker for
     * Caffeine's TTL bookkeeping.
     *
     * @param httpClient the HTTP client to use for skin fetching; must not be {@code null}
     */
    public SkinLoader(HttpClient httpClient) {
        this(httpClient, Ticker.systemTicker());
    }

    /**
     * Package-private test ctor: injects a {@link Ticker} so the username cache's TTL can
     * be simulated with a fake clock. Production callers go through the public 1-arg ctor.
     *
     * @param httpClient the HTTP client to use for skin fetching; must not be {@code null}
     * @param ticker the ticker backing the username cache's TTL; must not be {@code null}
     */
    SkinLoader(HttpClient httpClient, Ticker ticker) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        Objects.requireNonNull(ticker, "ticker must not be null");
        this.skinCache = Caffeine.newBuilder()
                .maximumSize(SKIN_CACHE_MAX_SIZE)
                .expireAfterWrite(SKIN_CACHE_TTL)
                .build();
        this.skinByUsernameCache = Caffeine.newBuilder()
                .maximumSize(USERNAME_CACHE_MAX_SIZE)
                .expireAfterWrite(USERNAME_CACHE_TTL)
                .ticker(ticker)
                .build();
    }

    /**
     * Loads a skin image from either a Base64-encoded texture property or a direct URL.
     * If both are present, Base64 takes priority.
     *
     * @param base64Texture optional Base64-encoded Minecraft profile texture JSON
     * @param textureUrl optional direct URL to the skin image
     * @return the loaded skin image
     * @throws RenderException if loading fails
     */
    public BufferedImage loadSkin(Optional<String> base64Texture, Optional<String> textureUrl) throws RenderException {
        if (base64Texture.isPresent()) {
            return loadFromBase64(base64Texture.get());
        }
        return loadFromUrl(textureUrl.get());
    }

    /**
     * Decodes a Base64 profile texture JSON, extracts the skin URL, and downloads the skin.
     *
     * @param base64Texture the Base64-encoded texture JSON
     * @return the loaded skin image
     * @throws RenderException if decoding, URL extraction, or download fails
     */
    public BufferedImage loadFromBase64(String base64Texture) throws RenderException {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Texture);
            String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);

            Matcher urlMatcher = URL_PATTERN.matcher(json);
            if (!urlMatcher.find()) {
                throw new RenderException(
                        "Could not find texture URL in decoded Base64 profile texture",
                        Map.of("json", json)
                );
            }
            String skinUrl = urlMatcher.group(1);
            return loadFromUrl(skinUrl);
        } catch (IllegalArgumentException e) {
            throw new RenderException(
                    "Invalid Base64 texture string",
                    Map.of("error", e.getMessage()),
                    e
            );
        }
    }

    /**
     * Downloads a skin image from a direct URL.
     *
     * @param skinUrl the skin image URL
     * @return the loaded skin image
     * @throws RenderException if the download or image decoding fails
     */
    public BufferedImage loadFromUrl(String skinUrl) throws RenderException {
        BufferedImage cached = skinCache.getIfPresent(skinUrl);
        if (cached != null) {
            return cached;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(skinUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RenderException(
                        "Failed to fetch skin: HTTP " + response.statusCode(),
                        Map.of("url", skinUrl, "statusCode", String.valueOf(response.statusCode()))
                );
            }

            try (InputStream in = response.body()) {
                BufferedImage skin = ImageIO.read(in);
                if (skin == null) {
                    throw new RenderException(
                            "Failed to decode skin image from URL (ImageIO returned null)",
                            Map.of("url", skinUrl, "statusCode", String.valueOf(response.statusCode()))
                    );
                }
                skinCache.put(skinUrl, skin);
                return skin;
            }
        } catch (IOException e) {
            throw new RenderException(
                    "Failed to load skin from URL: " + skinUrl,
                    Map.of("url", skinUrl),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderException(
                    "Skin fetch interrupted for URL: " + skinUrl,
                    Map.of("url", skinUrl),
                    e
            );
        } catch (IllegalArgumentException e) {
            throw new RenderException(
                    "Invalid skin URL: " + skinUrl,
                    Map.of("url", skinUrl),
                    e
            );
        }
    }

    /**
     *  third entry-point: loads a skin by Mojang username via
     * {@code api.mojang.com/users/profiles/minecraft/<name>} (username -> UUID) followed by
     * {@code sessionserver.mojang.com/session/minecraft/profile/<uuid>?unsigned=true}
     * (UUID -> SKIN url), then fetches the resulting PNG.
     *
     * <p>Results are cached with a 24h TTL, bounded at 1024 entries, dedupe-keyed by
     * lowercased username. Stale skins beat being rate limited by {@code api.mojang.com}.
     *
     * @param username the Mojang username; must not be {@code null}
     * @return the loaded skin image
     * @throws RenderException if the network lookup or PNG download fails
     * @throws ParseException if either JSON response is malformed
     */
    public BufferedImage loadByUsername(String username) throws RenderException, ParseException {
        Objects.requireNonNull(username, "username must not be null");
        String key = username.toLowerCase(Locale.ROOT);
        BufferedImage cached = skinByUsernameCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        UUID uuid = fetchUuidByUsername(username);
        String skinUrl = fetchSkinUrlByUuid(uuid);
        BufferedImage skin = fetchSkinBytes(skinUrl);
        skinByUsernameCache.put(key, skin);
        return skin;
    }

    private UUID fetchUuidByUsername(String username) throws RenderException, ParseException {
        URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/"
                + URLEncoder.encode(username, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status == 404) {
                throw new RenderException(
                        "Mojang username not found: " + username,
                        Map.of("username", username, "statusCode", status));
            }
            if (status == 429) {
                String retryAfter = resp.headers().firstValue("Retry-After").orElse("?");
                throw new RenderException(
                        "rate limited by api.mojang.com (Retry-After: " + retryAfter + "s)",
                        Map.of("username", username, "retryAfter", retryAfter));
            }
            if (status != 200) {
                throw new RenderException(
                        "unexpected status " + status + " from api.mojang.com",
                        Map.of("username", username, "statusCode", status));
            }
            return parseUuidFromProfileResponse(resp.body());
        } catch (IOException e) {
            throw new RenderException("IO error fetching username: " + username,
                    Map.of("username", username), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderException("username lookup interrupted: " + username,
                    Map.of("username", username), e);
        }
    }

    private static UUID parseUuidFromProfileResponse(String body) throws ParseException {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                throw new ParseException("api.mojang.com username lookup returned non-object JSON: " + body);
            }
            JsonElement idNode = root.getAsJsonObject().get("id");
            if (idNode == null || !idNode.isJsonPrimitive()) {
                throw new ParseException("api.mojang.com username lookup missing 'id' field: " + body);
            }
            String raw = idNode.getAsString();
            if (raw.length() == 32) {
                // Mojang returns undashed UUIDs; reinsert the canonical dashes.
                raw = raw.substring(0, 8) + "-"
                        + raw.substring(8, 12) + "-"
                        + raw.substring(12, 16) + "-"
                        + raw.substring(16, 20) + "-"
                        + raw.substring(20, 32);
            }
            return UUID.fromString(raw);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new ParseException("could not parse api.mojang.com username lookup response: " + e.getMessage());
        }
    }

    private String fetchSkinUrlByUuid(UUID uuid) throws RenderException, ParseException {
        String uuidNoDash = uuid.toString().replace("-", "");
        URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"
                + uuidNoDash + "?unsigned=true");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status == 429) {
                String retryAfter = resp.headers().firstValue("Retry-After").orElse("?");
                throw new RenderException(
                        "rate limited by sessionserver.mojang.com (Retry-After: " + retryAfter + "s)",
                        Map.of("uuid", uuid.toString(), "retryAfter", retryAfter));
            }
            if (status != 200) {
                throw new RenderException(
                        "unexpected status " + status + " from sessionserver.mojang.com",
                        Map.of("uuid", uuid.toString(), "statusCode", status));
            }
            return parseSkinUrlFromProfile(resp.body());
        } catch (IOException e) {
            throw new RenderException("IO error fetching profile for uuid: " + uuid,
                    Map.of("uuid", uuid.toString()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderException("profile lookup interrupted for uuid: " + uuid,
                    Map.of("uuid", uuid.toString()), e);
        }
    }

    private static String parseSkinUrlFromProfile(String body) throws ParseException {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                throw new ParseException("sessionserver profile returned non-object JSON: " + body);
            }
            JsonArray props = root.getAsJsonObject().getAsJsonArray("properties");
            if (props == null || props.isEmpty()) {
                throw new ParseException("sessionserver profile missing 'properties' array: " + body);
            }
            for (JsonElement el : props) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                JsonElement nameEl = obj.get("name");
                JsonElement valueEl = obj.get("value");
                if (nameEl == null || valueEl == null) continue;
                if (!"textures".equals(nameEl.getAsString())) continue;
                String base64 = valueEl.getAsString();
                byte[] decoded = Base64.getDecoder().decode(base64);
                String json = new String(decoded, StandardCharsets.UTF_8);
                JsonObject texturesRoot = JsonParser.parseString(json).getAsJsonObject();
                JsonObject textures = texturesRoot.getAsJsonObject("textures");
                if (textures == null) {
                    throw new ParseException("sessionserver textures property missing 'textures' key: " + json);
                }
                JsonObject skin = textures.getAsJsonObject("SKIN");
                if (skin == null) {
                    throw new ParseException("sessionserver textures missing 'SKIN' entry: " + json);
                }
                JsonElement urlEl = skin.get("url");
                if (urlEl == null || !urlEl.isJsonPrimitive()) {
                    throw new ParseException("sessionserver SKIN entry missing 'url' field: " + json);
                }
                return urlEl.getAsString();
            }
            throw new ParseException("sessionserver profile has no 'textures' property entry: " + body);
        } catch (JsonSyntaxException | IllegalArgumentException | IllegalStateException e) {
            throw new ParseException("could not parse sessionserver profile response: " + e.getMessage());
        }
    }

    private BufferedImage fetchSkinBytes(String skinUrl) throws RenderException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(skinUrl))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = resp.statusCode();
            if (status != 200) {
                throw new RenderException(
                        "Failed to fetch skin bytes: HTTP " + status,
                        Map.of("url", skinUrl, "statusCode", status));
            }
            try (InputStream in = new ByteArrayInputStream(resp.body())) {
                BufferedImage skin = ImageIO.read(in);
                if (skin == null) {
                    throw new RenderException(
                            "Failed to decode skin image (ImageIO returned null)",
                            Map.of("url", skinUrl));
                }
                return skin;
            }
        } catch (IOException e) {
            throw new RenderException("IO error downloading skin: " + skinUrl,
                    Map.of("url", skinUrl), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderException("skin download interrupted: " + skinUrl,
                    Map.of("url", skinUrl), e);
        }
    }
}
