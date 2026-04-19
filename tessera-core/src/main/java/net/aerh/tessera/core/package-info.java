/**
 * Tessera default implementation. {@code DefaultEngine} lives here along with the sealed
 * {@code CoreRenderRequest} hierarchy, built-in generators, caches, resource-pack readers,
 * and the bounded virtual-thread executor. Consumer code must never reach into this package
 * directly; downstream {@link net.aerh.tessera.testkit.archunit.DownstreamArchUnitRules} catches
 * violations.
 */
package net.aerh.tessera.core;
