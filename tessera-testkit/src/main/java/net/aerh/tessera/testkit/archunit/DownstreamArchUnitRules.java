package net.aerh.tessera.testkit.archunit;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Public downstream ArchUnit rules that Tessera consumers embed in their own test suites.
 *
 * <p>Ships as <em>main-scope</em> public API of {@code tessera-testkit} (not test-scope) so
 * consumers can add {@code tessera-testkit} to their test classpath and call the rules
 * directly from their own {@code @Test} methods. Enforces the boundary rule that consumer
 * code must depend only on {@code tessera-api} + {@code tessera-spi}, never on
 * {@code tessera-core}.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * import com.tngtech.archunit.core.domain.JavaClasses;
 * import com.tngtech.archunit.core.importer.ClassFileImporter;
 * import net.aerh.tessera.testkit.archunit.DownstreamArchUnitRules;
 * import org.junit.jupiter.api.Test;
 *
 * class MyProjectArchitectureTest {
 *
 *     @Test
 *     void my_project_does_not_reach_into_tessera_core() {
 *         JavaClasses imported = new ClassFileImporter().importPackages("com.my.project");
 *         DownstreamArchUnitRules.noImportsFromTesseraCore().check(imported);
 *     }
 * }
 * }</pre>
 */
public final class DownstreamArchUnitRules {

    private DownstreamArchUnitRules() {
        // utility class; not instantiable
    }

    /**
     * Rule: no class may depend on any class residing in {@code net.aerh.tessera.core..}.
     * Fails the test with ArchUnit's standard assertion error, naming every offending class.
     *
     * @return a fresh {@link ArchRule} instance; safe to check against any {@code JavaClasses}.
     */
    public static ArchRule noImportsFromTesseraCore() {
        return noClasses().should()
                .dependOnClassesThat().resideInAPackage("net.aerh.tessera.core..")
                .because("consumers must use tessera-api + tessera-spi only; "
                        + "reaching into tessera-core breaks forward-compatibility");
    }
}
