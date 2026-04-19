package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.exception.ParseException;
import net.aerh.tessera.api.exception.RenderException;
import net.aerh.tessera.api.exception.RenderFailedException;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.generator.PlayerHeadBuilder;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Package-private fluent {@link PlayerHeadBuilder} implementation. Constructed via
 * {@link DefaultEngine#playerHead()}.
 *
 * <p>{@link #username(String)} stores the value as the {@code playerName} display field;
 * username-driven texture lookup happens inside {@link net.aerh.tessera.core.generator.PlayerHeadGenerator}
 * when neither {@code base64Texture} nor {@code textureUrl} resolves through the SkinLoader.
 * The existing two-source contract (base64 or URL) is preserved on the fluent surface.
 */
public final class PlayerHeadBuilderImpl implements PlayerHeadBuilder, InternalRequestSource {

    private final DefaultEngine engine;
    private Optional<String> base64Texture = Optional.empty();
    private Optional<String> textureUrl = Optional.empty();
    private Optional<String> playerName = Optional.empty();
    private int scale = 1;
    private GenerationContext context = GenerationContext.defaults();

    public PlayerHeadBuilderImpl(DefaultEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    @Override
    public PlayerHeadBuilder username(String username) {
        this.playerName = Optional.of(Objects.requireNonNull(username, "username must not be null"));
        return this;
    }

    @Override
    public PlayerHeadBuilder base64Texture(String base64) {
        this.base64Texture = Optional.of(Objects.requireNonNull(base64, "base64 must not be null"));
        return this;
    }

    @Override
    public PlayerHeadBuilder textureUrl(String url) {
        this.textureUrl = Optional.of(Objects.requireNonNull(url, "url must not be null"));
        return this;
    }

    @Override
    public PlayerHeadBuilder playerName(String playerName) {
        this.playerName = Optional.of(Objects.requireNonNull(playerName, "playerName must not be null"));
        return this;
    }

    @Override
    public PlayerHeadBuilder scale(int scale) {
        this.scale = scale;
        return this;
    }

    @Override
    public PlayerHeadBuilder context(GenerationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        return this;
    }

    @Override
    public GeneratorResult render() {
        PlayerHeadRequest request = buildRequest();
        try {
            return engine.renderInternal(request, context);
        } catch (RenderException | ParseException e) {
            throw new RenderFailedException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<GeneratorResult> renderAsync() {
        PlayerHeadRequest request = buildRequest();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return engine.renderInternal(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(new RenderFailedException(e.getMessage(), e));
            }
        }, engine.executor());
    }

    private PlayerHeadRequest buildRequest() {
        return new PlayerHeadRequest(base64Texture, textureUrl, playerName, scale);
    }

    @Override
    public PlayerHeadRequest toInternalRequest() {
        return buildRequest();
    }
}
