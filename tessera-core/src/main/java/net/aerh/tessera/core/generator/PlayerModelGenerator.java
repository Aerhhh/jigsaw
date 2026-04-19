package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.core.generator.player.ArmorSet;
import net.aerh.tessera.core.generator.player.ArmorTexture;
import net.aerh.tessera.core.generator.player.IsometricPlayerRenderer;
import net.aerh.tessera.core.image.ImageOps;
import net.aerh.tessera.api.exception.RenderException;

import java.awt.image.BufferedImage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * Renders an isometric 3D player model from a Minecraft skin.
 *
 * <p>The skin is loaded from either a Base64-encoded profile texture property or a direct
 * URL, then rendered as a full 3D isometric body using {@link IsometricPlayerRenderer}.
 * Supports slim (Alex) and classic (Steve) arm models and optional armor rendering
 * via {@link ArmorTexture}.
 */
public final class PlayerModelGenerator implements Generator<PlayerModelRequest, GeneratorResult> {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final SkinLoader skinLoader;
    private final ArmorTexture armorTexture;

    /**
     * Creates a new generator with the given HTTP client and armor texture loader.
     *
     * @param httpClient HTTP client for skin fetching
     * @param armorTexture armor texture loader for resolving material names
     */
    public PlayerModelGenerator(HttpClient httpClient, ArmorTexture armorTexture) {
        this.skinLoader = new SkinLoader(Objects.requireNonNull(httpClient, "httpClient must not be null"));
        this.armorTexture = Objects.requireNonNull(armorTexture, "armorTexture must not be null");
    }

    /**
     * Creates a generator with a default HTTP client and armor texture loader.
     */
    public static PlayerModelGenerator withDefaults() {
        return withDefaults(new ArmorTexture());
    }

    /**
     * Creates a generator with a default HTTP client and the given armor texture loader.
     */
    public static PlayerModelGenerator withDefaults(ArmorTexture armorTexture) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        return new PlayerModelGenerator(client, armorTexture);
    }

    @Override
    public GeneratorResult render(PlayerModelRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage skin = skinLoader.loadSkin(input.base64Texture(), input.textureUrl());

        ArmorSet armor = input.armor().orElse(null);
        BufferedImage body = IsometricPlayerRenderer.render(skin, input.slim(), armor, armorTexture);

        if (input.scale() > 1) {
            body = ImageOps.upscaleNearestNeighbor(body, input.scale());
        }

        return new GeneratorResult.StaticImage(body);
    }

    @Override
    public Class<PlayerModelRequest> inputType() {
        return PlayerModelRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }
}
