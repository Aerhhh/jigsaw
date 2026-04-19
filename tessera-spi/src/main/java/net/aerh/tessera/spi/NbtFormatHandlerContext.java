package net.aerh.tessera.spi;

import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.data.RegistryKey;

import java.util.Optional;

/**
 * Context provided to {@link NbtFormatHandler} instances during parsing.
 * <p>
 * Grants access to registered data registries without coupling handlers to the engine.
 *
 * @see NbtFormatHandler
 */
public interface NbtFormatHandlerContext {

    /**
     * Returns the {@link DataRegistry} registered under the given key, or empty if none is registered.
     *
     * @param key The registry key to look up.
     * @param <T> The registry value type.
     */
    <T> Optional<DataRegistry<T>> registry(RegistryKey<T> key);
}
