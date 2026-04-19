package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.exception.RegistryException;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.core.generator.CoreRenderRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime check run at {@code Engine.builder().build()} time.
 * Iterates {@link CoreRenderRequest#getPermittedSubclasses()}, verifies every
 * permits-subtype has a registered {@link Generator}; any miss throws
 * {@link RegistryException} with the full list of unmapped types.
 *
 * <p>Adding a new permits subtype without wiring a generator is a RUNTIME failure
 * at engine build time (fail-fast before the first render) - symmetric with the
 * compile-time failure in {@code DefaultEngine#renderInternal}'s exhaustive pattern
 * switch.
 *
 * <p>Public-but-core-internal:   relocated {@code DefaultEngineBuilder} into
 * {@code core.generator}, so this startup-check helper's visibility was widened from
 * package-private to public so the cross-package build() can still invoke it. Consumer-jar
 * isolation is preserved by {@code DownstreamArchUnitRules#noImportsFromTesseraCore}

 */
public final class StartupCheck {

    private StartupCheck() {
        /* static-only */
    }

    /**
     * Verifies that every {@link CoreRenderRequest} permitted-subtype has an entry in
     * {@code registered}. Any miss fails fast with a {@link RegistryException} naming
     * all missing types.
     *
     * @param registered the {@code Class} -> {@code Generator} map built by
     *                   {@code DefaultEngineBuilder.build()}
     * @throws NullPointerException if {@code registered} is null
     * @throws RegistryException if one or more permitted subtypes have no mapping
     */
    public static void verifyAllCoreRequestsHaveGenerators(
            Map<Class<? extends CoreRenderRequest>, Generator<?, ?>> registered) {
        Objects.requireNonNull(registered, "registered must not be null");
        List<String> missing = new ArrayList<>();
        for (Class<?> permitted : CoreRenderRequest.class.getPermittedSubclasses()) {
            if (!registered.containsKey(permitted)) {
                missing.add(permitted.getName());
            }
        }
        if (!missing.isEmpty()) {
            throw new RegistryException(
                    "No GeneratorFactory registered for built-in request types: " + missing
                            + ". Check Engine.builder() wiring; built-in generators are registered "
                            + "by default unless EngineBuilder#noDefaults() was called.",
                    Map.of("missing", missing));
        }
    }
}
