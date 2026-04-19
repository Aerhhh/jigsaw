package net.aerh.tessera.testkit.fixtures.violating;

/**
 * Deliberately violates the downstream "no imports of net.aerh.tessera.core.*" rule
 * so {@link net.aerh.tessera.testkit.archunit.DownstreamRuleWiringTest} can prove
 * the rule fires.
 *
 * <p>This class is classpath-scoped to {@code src/test/java} of {@code tessera-testkit}
 * ONLY - it never ships in the testkit's jar. Consumers of tessera-testkit never see
 * this class transitively because the tessera-core dep that makes it compile is
 * test-scope in tessera-testkit's pom.
 */
@SuppressWarnings("unused")
public final class ImportsCoreFixture {
    // Deliberate: reference a tessera-core class so ArchUnit picks it up as a dependency.
    private final Class<?> reference = net.aerh.tessera.core.generator.DefaultEngine.class;
}
