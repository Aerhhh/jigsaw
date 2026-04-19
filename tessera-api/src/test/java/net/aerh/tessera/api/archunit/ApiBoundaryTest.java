package net.aerh.tessera.api.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module-boundary rules for {@code tessera-api}. These rules enforce that no api class
 * accidentally imports {@code tessera-core} or {@code tessera-spi}, the two sibling modules
 * the api must remain independent of.
 */
class ApiBoundaryTest {

    private final JavaClasses apiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("net.aerh.tessera.api");

    @Test
    void api_does_not_depend_on_core() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("net.aerh.tessera.api..")
                .should().dependOnClassesThat().resideInAPackage("net.aerh.tessera.core..");
        rule.check(apiClasses);
    }

    @Test
    void api_does_not_depend_on_spi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("net.aerh.tessera.api..")
                .should().dependOnClassesThat().resideInAPackage("net.aerh.tessera.spi..");
        rule.check(apiClasses);
    }

    @Test
    void api_has_at_least_the_expected_surface_classes() {
        // Sanity guard: if someone deletes every class from tessera-api, ArchUnit checks
        // trivially pass (0 classes -> 0 violations). Count guard catches that.
        // Lower bound: 13 exceptions + 8 leak-destinations + 7 fluent builders + RenderSpec
        // + Capabilities + AssetProvider + ClosedEngineException + RenderFailedException
        // + the ~30 api/ subpackage files = ~60+; floor of 30 is conservative.
        Assertions.assertThat(apiClasses.size()).isGreaterThanOrEqualTo(30);
    }
}
