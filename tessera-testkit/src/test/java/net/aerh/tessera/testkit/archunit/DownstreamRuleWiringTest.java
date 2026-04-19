package net.aerh.tessera.testkit.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wiring test for {@link DownstreamArchUnitRules#noImportsFromTesseraCore()}.
 *
 * <p>Backed by a planted-violation fixture. The test class
 * {@link net.aerh.tessera.testkit.fixtures.violating.ImportsCoreFixture}
 * deliberately references {@code net.aerh.tessera.core.generator.DefaultEngine};
 * this test asserts the rule flags that fixture with an {@link AssertionError}
 * whose message names the forbidden package.
 */
class DownstreamRuleWiringTest {

    @Test
    void rule_is_well_formed_and_has_the_expected_description() {
        ArchRule rule = DownstreamArchUnitRules.noImportsFromTesseraCore();
        assertThat(rule).isNotNull();
        assertThat(rule.getDescription())
                .contains("net.aerh.tessera.core")
                .contains("tessera-api + tessera-spi");
    }

    @Test
    void rule_passes_on_classes_that_do_not_import_tessera_core() {
        // java.lang.String has no dep on tessera.core; rule passes.
        JavaClasses clean = new ClassFileImporter().importClasses(String.class, java.util.List.class);
        assertThatCode(() -> DownstreamArchUnitRules.noImportsFromTesseraCore().check(clean))
                .doesNotThrowAnyException();
    }

    @Test
    void rule_fires_on_planted_core_import() {
        JavaClasses violating = new ClassFileImporter()
                .importPackages("net.aerh.tessera.testkit.fixtures.violating");
        assertThat(violating.size())
                .as("fixture package must be on the classpath")
                .isGreaterThan(0);

        assertThatThrownBy(() -> DownstreamArchUnitRules.noImportsFromTesseraCore().check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("net.aerh.tessera.core");
    }
}
