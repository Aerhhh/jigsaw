package net.aerh.tessera.core.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reactor-wide ArchUnit gate for {@link net.aerh.tessera.api.image.Graphics2DFactory}.
 *
 * <p>Every class under {@code net.aerh.tessera..} must route {@code Graphics2D}
 * construction through the factory so the four mandatory rendering hints (AA=OFF,
 * TextAA=OFF, FM=OFF, Interp=NN) are applied uniformly. The {@code net.aerh.skyblock..}
 * namespace is implicitly excluded (consumer, not library artifact).
 *
 * <p>This gate runs in <strong>enforced</strong> mode: every surviving
 * {@code BufferedImage.createGraphics()} / {@code getGraphics()} call site under
 * {@code net.aerh.tessera..} has been migrated onto
 * {@code Graphics2DFactory.createGraphics(img)}, so {@code rule.check(reactorClasses)}
 * asserts a green baseline on every {@code mvn verify}.
 *
 * <p>The rule exempts three categories of classes:
 * <ul>
 *   <li>{@code Graphics2DFactory} itself - its job is to call {@code createGraphics}.</li>
 *   <li>Classes whose simple name ends with {@code Test} - test fixtures occasionally need
 *   raw Graphics2D to build synthetic inputs.</li>
 *   <li>Implicitly, every non-tessera package - ArchUnit's
 *   {@code importPackages("net.aerh.tessera")} filter keeps the skyblock consumer module
 *   outside the rule's scope.</li>
 * </ul>
 *
 * @see <a href="https://github.com/TNG/ArchUnit/issues/981">ArchUnit issue #981</a> for the
 * known-gotcha that {@code callMethod} does not trap reflective invocation. Adversarial
 * bypass is not the threat model here; accidental regression is.
 */
class Graphics2DFactoryArchTest {

    @Test
    void only_Graphics2DFactory_may_instantiate_Graphics2D() {
        JavaClasses reactorClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("net.aerh.tessera"); // covers all 6 tessera-* modules; skyblock excluded.

        ArchRule rule = noClasses()
                .that().resideInAPackage("net.aerh.tessera..")
                .and().haveNameNotMatching(".*Graphics2DFactory")
                .and().haveSimpleNameNotEndingWith("Test")
                .should().callMethod(BufferedImage.class, "createGraphics")
                .orShould().callMethod(BufferedImage.class, "getGraphics")
                .because("every Graphics2D construction must route through Graphics2DFactory "
                        + "to apply the 4 mandatory rendering hints (AA=OFF, TextAA=OFF, FM=OFF, Interp=NN).");

        // Enforced mode: a raw createGraphics()/getGraphics() under net.aerh.tessera.. fails
        // the build. Every surviving call site has already been rewired through Graphics2DFactory.
        rule.check(reactorClasses);
    }
}
