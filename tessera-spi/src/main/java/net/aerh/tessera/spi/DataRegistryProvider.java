package net.aerh.tessera.spi;

import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.data.RegistryKey;

/**
 * SPI contract for contributing a {@link DataRegistry} to the engine.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} or explicit registration.
 *
 * @see net.aerh.tessera.api.data.DataRegistry
 * @see net.aerh.tessera.api.data.RegistryKey
 */
public interface DataRegistryProvider {

    /**
     * The key under which the registry produced by {@link #create()} will be stored.
     */
    RegistryKey<?> key();

    /**
     * Creates and returns the registry. Called once during engine initialization.
     */
    DataRegistry<?> create();
}
