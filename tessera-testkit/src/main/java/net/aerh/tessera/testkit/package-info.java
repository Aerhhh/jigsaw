/**
 * Consumer test-support artifacts. Ships
 * {@link net.aerh.tessera.testkit.archunit.DownstreamArchUnitRules}, a downstream
 * ArchUnit rule that forbids consumer code from importing {@code net.aerh.tessera.core},
 * so consumer test suites can reuse the check from their own test classes. Golden-image
 * assertions and fixture helpers ship alongside the ArchUnit rule.
 */
package net.aerh.tessera.testkit;
