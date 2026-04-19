/**
 * Self-hosted Tessera HTTP service (future scope). The module currently ships only the
 * empty skeleton; handlers, OpenAPI wiring, security, and metrics arrive in a later phase.
 * The {@link net.aerh.tessera.http.archunit.HttpBoundaryTest} boundary rule enforces that
 * only classes under {@link net.aerh.tessera.http.bootstrap} may import
 * {@code net.aerh.tessera.core..}.
 */
package net.aerh.tessera.http;
