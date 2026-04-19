package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.PlayerModelBuilder;
import net.aerh.tessera.core.generator.player.ArmorSet;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Package-private fluent {@link PlayerModelBuilder} implementation. Constructed via
 * {@link DefaultEngine#playerModel()}.
 *
 * <p>The api-level {@code armor(Object)} setter accepts an opaque {@link Object} to avoid an
 * api -> core dependency on {@code ArmorSet}; this impl casts at the call site.
 */
public final class PlayerModelBuilderImpl implements PlayerModelBuilder, InternalRequestSource {

    private final DefaultEngine engine;
    private Optional<String> base64Texture = Optional.empty();
    private Optional<String> textureUrl = Optional.empty();
    private Optional<String> playerName = Optional.empty();
    private Optional<ArmorSet> armor = Optional.empty();
    private boolean slim;
    private int scale = 1;
    private GenerationContext context = GenerationContext.defaults();

    public PlayerModelBuilderImpl(DefaultEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    @Override
    public PlayerModelBuilder username(String username) {
        this.playerName = Optional.of(Objects.requireNonNull(username, "username must not be null"));
        return this;
    }

    @Override
    public PlayerModelBuilder base64Texture(String base64) {
        this.base64Texture = Optional.of(Objects.requireNonNull(base64, "base64 must not be null"));
        return this;
    }

    @Override
    public PlayerModelBuilder textureUrl(String url) {
        this.textureUrl = Optional.of(Objects.requireNonNull(url, "url must not be null"));
        return this;
    }

    @Override
    public PlayerModelBuilder playerName(String playerName) {
        this.playerName = Optional.of(Objects.requireNonNull(playerName, "playerName must not be null"));
        return this;
    }

    @Override
    public PlayerModelBuilder armor(Object armorSet) {
        Objects.requireNonNull(armorSet, "armorSet must not be null");
        if (!(armorSet instanceof ArmorSet typed)) {
            throw new IllegalArgumentException(
                    "armorSet must be an instance of net.aerh.tessera.core.generator.player.ArmorSet, got: "
                            + armorSet.getClass().getName());
        }
        this.armor = Optional.of(typed);
        return this;
    }

    @Override
    public PlayerModelBuilder slim(boolean slim) {
        this.slim = slim;
        return this;
    }

    @Override
    public PlayerModelBuilder scale(int scale) {
        this.scale = scale;
        return this;
    }

    @Override
    public PlayerModelBuilder context(GenerationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        return this;
    }

    @Override
    public GeneratorResult render() {
        PlayerModelRequest request = buildRequest();
        try {
            return engine.renderInternal(request, context);
        } catch (RenderException | ParseException e) {
            throw new RenderFailedException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<GeneratorResult> renderAsync() {
        PlayerModelRequest request = buildRequest();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return engine.renderInternal(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(new RenderFailedException(e.getMessage(), e));
            }
        }, engine.executor());
    }

    private PlayerModelRequest buildRequest() {
        return new PlayerModelRequest(base64Texture, textureUrl, playerName, armor, slim, scale);
    }

    @Override
    public PlayerModelRequest toInternalRequest() {
        return buildRequest();
    }
}
