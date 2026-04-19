/**
 * Tessera extension SPI contracts. Stable interfaces consumers and built-in modules implement
 * to plug into the engine (generators, effects, font providers, NBT handlers, overlay renderers,
 * data registries). Must not depend on {@code net.aerh.tessera.core} (enforced by
 * {@link net.aerh.tessera.spi.archunit.SpiBoundaryTest}).
 */
package net.aerh.tessera.spi;
