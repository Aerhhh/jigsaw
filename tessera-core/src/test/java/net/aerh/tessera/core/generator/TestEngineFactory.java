package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.assets.Capabilities;
import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.nbt.NbtParser;
import net.aerh.tessera.api.overlay.OverlayColorProvider;
import net.aerh.tessera.api.sprite.SpriteProvider;
import net.aerh.tessera.core.nbt.DefaultNbtParser;
import net.aerh.tessera.core.nbt.handler.ComponentsNbtFormatHandler;
import net.aerh.tessera.core.nbt.handler.DefaultNbtFormatHandler;
import net.aerh.tessera.core.nbt.handler.PostFlatteningNbtFormatHandler;
import net.aerh.tessera.core.nbt.handler.PreFlatteningNbtFormatHandler;

import java.awt.image.BufferedImage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Test-only factory that materialises a bare {@link DefaultEngine} without touching
 * the asset cache. Lives in the {@code engine} package so it can call the
 * package-private {@link DefaultEngine} constructor directly.
 *
 * <p>Used by tests that exercise engine-boundary behaviour (e.g. fluent-builder
 * exception wrapping) without requiring a live {@code TesseraAssets.fetch} cache
 * or a real sprite atlas. The returned engine has stub generators whose
 * {@code render} throws; tests must avoid reaching them.
 */
public final class TestEngineFactory {

    private TestEngineFactory() {
    }

    /**
     * Returns a {@link DefaultEngine} wired with a real {@link DefaultNbtParser} (all four
     * built-in handlers) and throwing stub generators.
     */
    public static DefaultEngine minimalEngine() {
        NbtParser nbtParser = new DefaultNbtParser(List.of(
                new ComponentsNbtFormatHandler(),
                new PostFlatteningNbtFormatHandler(),
                new PreFlatteningNbtFormatHandler(),
                new DefaultNbtFormatHandler()));

        Executor executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-engine-executor");
            t.setDaemon(true);
            return t;
        });

        return new DefaultEngine(
                stubSpriteProvider(),
                stubItemGenerator(),
                stubTooltipGenerator(),
                stubInventoryGenerator(),
                stubPlayerHeadGenerator(),
                stubPlayerModelGenerator(),
                nbtParser,
                Map.<String, DataRegistry<?>>of(),
                OverlayColorProvider.fromDefaults(),
                new Capabilities(true, true, true, "test"),
                executor,
                false,
                Duration.ofSeconds(1),
                List.of(),
                HttpClient.newHttpClient(),
                net.aerh.tessera.api.image.OutputSizeGate.DEFAULT_STATIC_CAP,
                net.aerh.tessera.api.image.OutputSizeGate.DEFAULT_ANIMATED_CAP);
    }

    private static SpriteProvider stubSpriteProvider() {
        return new SpriteProvider() {
            @Override
            public Optional<BufferedImage> getSprite(String textureId) {
                return Optional.empty();
            }

            @Override
            public Collection<String> availableSprites() {
                return Collections.emptyList();
            }

            @Override
            public Optional<BufferedImage> search(String query) {
                return Optional.empty();
            }

            @Override
            public List<Map.Entry<String, BufferedImage>> searchAll(String query) {
                return Collections.emptyList();
            }

            @Override
            public Map<String, BufferedImage> getAllSprites() {
                return Collections.emptyMap();
            }
        };
    }

    private static Generator<ItemRequest, GeneratorResult> stubItemGenerator() {
        return new Generator<>() {
            @Override
            public GeneratorResult render(ItemRequest input, net.aerh.tessera.api.generator.GenerationContext context) {
                throw new UnsupportedOperationException("stub item generator");
            }

            @Override
            public Class<ItemRequest> inputType() {
                return ItemRequest.class;
            }

            @Override
            public Class<GeneratorResult> outputType() {
                return GeneratorResult.class;
            }
        };
    }

    private static Generator<TooltipRequest, GeneratorResult> stubTooltipGenerator() {
        return new Generator<>() {
            @Override
            public GeneratorResult render(TooltipRequest input, net.aerh.tessera.api.generator.GenerationContext context) {
                throw new UnsupportedOperationException("stub tooltip generator");
            }

            @Override
            public Class<TooltipRequest> inputType() {
                return TooltipRequest.class;
            }

            @Override
            public Class<GeneratorResult> outputType() {
                return GeneratorResult.class;
            }
        };
    }

    private static Generator<InventoryRequest, GeneratorResult> stubInventoryGenerator() {
        return new Generator<>() {
            @Override
            public GeneratorResult render(InventoryRequest input, net.aerh.tessera.api.generator.GenerationContext context) {
                throw new UnsupportedOperationException("stub inventory generator");
            }

            @Override
            public Class<InventoryRequest> inputType() {
                return InventoryRequest.class;
            }

            @Override
            public Class<GeneratorResult> outputType() {
                return GeneratorResult.class;
            }
        };
    }

    private static Generator<PlayerHeadRequest, GeneratorResult> stubPlayerHeadGenerator() {
        return new Generator<>() {
            @Override
            public GeneratorResult render(PlayerHeadRequest input, net.aerh.tessera.api.generator.GenerationContext context) {
                throw new UnsupportedOperationException("stub player head generator");
            }

            @Override
            public Class<PlayerHeadRequest> inputType() {
                return PlayerHeadRequest.class;
            }

            @Override
            public Class<GeneratorResult> outputType() {
                return GeneratorResult.class;
            }
        };
    }

    private static Generator<PlayerModelRequest, GeneratorResult> stubPlayerModelGenerator() {
        return new Generator<>() {
            @Override
            public GeneratorResult render(PlayerModelRequest input, net.aerh.tessera.api.generator.GenerationContext context) {
                throw new UnsupportedOperationException("stub player model generator");
            }

            @Override
            public Class<PlayerModelRequest> inputType() {
                return PlayerModelRequest.class;
            }

            @Override
            public Class<GeneratorResult> outputType() {
                return GeneratorResult.class;
            }
        };
    }
}
