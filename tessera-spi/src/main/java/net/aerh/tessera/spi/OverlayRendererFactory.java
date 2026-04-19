package net.aerh.tessera.spi;

import net.aerh.tessera.api.overlay.OverlayRenderer;

/**
 * SPI contract for contributing an {@link OverlayRenderer} implementation.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} or explicit registration.
 *
 * @see net.aerh.tessera.api.overlay.OverlayRenderer
 */
public interface OverlayRendererFactory {

    /**
     * The renderer type key this factory produces (e.g. {@code "mig:normal"}).
     * Must match the {@code rendererType} field on the {@link net.aerh.tessera.api.overlay.Overlay} records
     * that this renderer handles.
     */
    String type();

    /**
     * Creates and returns a new overlay renderer instance.
     */
    OverlayRenderer create();
}
